# Secrets and Credentials Setup Guide

This document explains how to configure secrets for CI/CD pipelines.

## 🔐 GitHub Actions Secrets

### Required Secrets (for push-to-registry job)

To enable Docker image pushing to a container registry, configure these secrets in your GitHub repository:

**Go to**: Repository → Settings → Secrets and variables → Actions → New repository secret

| Secret Name | Description | Example Value |
|-------------|-------------|---------------|
| `DOCKER_REGISTRY` | Container registry URL | `docker.io` or `ghcr.io` or `your-registry.com` |
| `DOCKER_USERNAME` | Registry username | `your-dockerhub-username` |
| `DOCKER_PASSWORD` | Registry password/token | `dckr_pat_abc123...` (use access token, not password) |

### How to Create Docker Hub Access Token

1. Log in to [Docker Hub](https://hub.docker.com/)
2. Go to **Account Settings** → **Security** → **New Access Token**
3. Name: `github-actions-iss-tracker`
4. Permissions: **Read, Write, Delete**
5. Copy the token (you won't see it again!)
6. Add to GitHub secrets as `DOCKER_PASSWORD`

### How to Use GitHub Container Registry (GHCR)

Alternatively, use GitHub's own registry (no external account needed):

```yaml
DOCKER_REGISTRY: ghcr.io
DOCKER_USERNAME: ${{ github.actor }}
DOCKER_PASSWORD: ${{ secrets.GITHUB_TOKEN }}  # Auto-provided by GitHub
```

**Update workflow** to use GHCR:
```yaml
- name: Log in to GitHub Container Registry
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

---

## 🔧 Jenkins Credentials

### Required Jenkins Credentials

The Jenkinsfile expects these credentials to be configured in Jenkins:

#### 1. Docker Registry URL (Secret Text)
- **ID**: `docker-registry-url`
- **Type**: Secret text
- **Value**: `docker.io` or `ghcr.io` or your registry URL

#### 2. Docker Registry Credentials (Username with password)
- **ID**: `docker-registry-credentials`
- **Type**: Username with password
- **Username**: Your registry username
- **Password**: Your registry access token

#### 3. Deployment Credentials (per environment)
- **ID**: `deploy-dev-credentials`, `deploy-staging-credentials`, `deploy-prod-credentials`
- **Type**: SSH Username with private key
- **Username**: `deploy`
- **Private Key**: SSH private key for deployment server

### How to Add Credentials in Jenkins

1. Go to **Jenkins Dashboard** → **Manage Jenkins** → **Credentials**
2. Select **(global)** domain
3. Click **Add Credentials**
4. Fill in the form:
   - **Kind**: Username with password (for Docker) or SSH Username with private key (for deployment)
   - **ID**: Use exact IDs from table above
   - **Username**: Your username
   - **Password/Private Key**: Your secret
5. Click **Create**

### Environment Variables for Jenkins

Set these in Jenkins → Configure System → Global properties → Environment variables:

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `DEPLOY_HOST_DEV` | Dev server hostname | `dev.example.com` |
| `DEPLOY_HOST_STAGING` | Staging server hostname | `staging.example.com` |
| `DEPLOY_HOST_PROD` | Production server hostname | `prod.example.com` |

---

## 🚀 Quick Setup Commands

### For GitHub Actions

```bash
# Using GitHub CLI
gh secret set DOCKER_REGISTRY -b "docker.io"
gh secret set DOCKER_USERNAME -b "your-username"
gh secret set DOCKER_PASSWORD -b "your-token-here"

# Verify secrets are set
gh secret list
```

### For Jenkins (using Jenkins CLI)

```bash
# Add Docker registry URL
echo 'docker.io' | java -jar jenkins-cli.jar \
  -s http://localhost:8080/ \
  create-credentials-by-xml system::system::jenkins \
  "(global)" < docker-registry-url.xml

# Add Docker credentials
java -jar jenkins-cli.jar \
  -s http://localhost:8080/ \
  create-credentials-by-xml system::system::jenkins \
  "(global)" < docker-credentials.xml
```

---

## ⚠️ Current Workflow Behavior

### GitHub Actions

✅ **Without secrets configured:**
- ✅ Backend tests run
- ✅ Frontend tests run
- ✅ Docker build succeeds
- ✅ Trivy security scan runs
- ✅ Smoke tests pass
- ⏭️ **Push-to-registry job skipped** (secrets not available)
- ✅ **Overall workflow: SUCCESS**

✅ **With secrets configured:**
- ✅ All above steps run
- ✅ **Push-to-registry job runs**
- ✅ Image pushed to registry
- ✅ **Overall workflow: SUCCESS**

### Jenkins

❌ **Without credentials:**
- ✅ Checkout succeeds
- ✅ Backend tests pass
- ✅ Frontend tests pass
- ✅ Docker build succeeds
- ✅ Trivy scan runs
- ❌ **Push to Registry fails** (credentials not found)
- ⏭️ Deploy skipped
- ❌ **Pipeline: FAILURE**

✅ **With credentials:**
- ✅ All stages succeed
- ✅ Image pushed to registry
- ✅ Deployment succeeds
- ✅ **Pipeline: SUCCESS**

---

## 🔍 Troubleshooting

### GitHub Actions: "Username and password required"

**Cause**: `DOCKER_USERNAME` or `DOCKER_PASSWORD` secrets are not set or are empty.

**Fix**:
1. Go to repository Settings → Secrets → Actions
2. Verify all three secrets exist: `DOCKER_REGISTRY`, `DOCKER_USERNAME`, `DOCKER_PASSWORD`
3. If missing, add them following the guide above

### GitHub Actions: "Resource not accessible by integration"

**Cause**: Missing `security-events: write` permission for SARIF upload.

**Fix**: Already fixed in `.github/workflows/ci.yml` (permissions block added).

### Jenkins: "Credentials not found"

**Cause**: Jenkins can't find the credential with the specified ID.

**Fix**:
1. Go to Jenkins → Credentials
2. Verify the credential exists with exact ID: `docker-registry-credentials`
3. If missing, add it following the guide above

### Jenkins: Deployment fails with "Connection refused"

**Cause**: Deployment server hostname or SSH key is incorrect.

**Fix**:
1. Verify `DEPLOY_HOST_DEV` environment variable is set correctly
2. Verify SSH credentials have correct private key
3. Test SSH connection manually: `ssh -i /path/to/key deploy@dev.example.com`

---

## 📋 Checklist

### GitHub Actions Setup
- [ ] `DOCKER_REGISTRY` secret added
- [ ] `DOCKER_USERNAME` secret added
- [ ] `DOCKER_PASSWORD` secret added (use token, not password)
- [ ] Pushed a commit to trigger workflow
- [ ] Verified workflow runs successfully
- [ ] Checked Security tab for Trivy scan results

### Jenkins Setup
- [ ] Jenkins installed and running
- [ ] `docker-registry-url` credential added
- [ ] `docker-registry-credentials` credential added
- [ ] `deploy-dev-credentials` credential added (if deploying)
- [ ] `DEPLOY_HOST_DEV` environment variable set
- [ ] Pipeline created and linked to repository
- [ ] Pipeline runs successfully

---

## 🔗 References

- [GitHub Actions Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Docker Hub Access Tokens](https://docs.docker.com/docker-hub/access-tokens/)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [Jenkins Credentials](https://www.jenkins.io/doc/book/using/using-credentials/)
- [Trivy Security Scanner](https://github.com/aquasecurity/trivy)
