# CI/CD Pipeline Setup

Quick reference for setting up continuous integration and deployment.

## 🔄 Available Pipelines

### 1. GitHub Actions (`.github/workflows/ci.yml`)

**Triggers**: Push to `main`, Pull Requests

**Jobs**:
1. **Backend Tests** - Maven tests with JaCoCo coverage
2. **Frontend Tests** - Vitest with React Testing Library
3. **Docker Build & Scan** - Build image, Trivy security scan, smoke test
4. **Push to Registry** - Push to Docker registry (main branch only)

**Status**: ✅ Fully configured with graceful secret handling

### 2. Jenkins Pipeline (`Jenkinsfile`)

**Triggers**: Manual or webhook from Git

**Stages**:
1. Checkout
2. Backend Tests (with JaCoCo report)
3. Frontend Tests
4. Build Docker Image
5. Security Scan (Trivy)
6. Smoke Test
7. Push to Registry (main branch only)
8. Deploy (main/develop branches, per environment)

**Status**: ✅ Configured with error handling for missing credentials

---

## ⚡ Quick Start

### GitHub Actions

**No setup required!** Pipeline runs automatically on push/PR.

**Optional**: To enable registry push, add secrets:
```bash
gh secret set DOCKER_REGISTRY -b "docker.io"
gh secret set DOCKER_USERNAME -b "your-username"
gh secret set DOCKER_PASSWORD -b "your-token"
```

### Jenkins

1. **Install Jenkins** with Docker and Git plugins
2. **Create Pipeline Job**:
   - New Item → Pipeline
   - Pipeline → Definition → Pipeline script from SCM
   - SCM → Git → Repository URL
   - Script Path: `Jenkinsfile`
3. **Add Credentials** (see [SECRETS_SETUP.md](../SECRETS_SETUP.md))
4. **Run Pipeline**

---

## 🔐 Required Secrets

### GitHub Actions (Optional - for registry push)

| Secret | Description |
|--------|-------------|
| `DOCKER_REGISTRY` | Registry URL (e.g., `docker.io`) |
| `DOCKER_USERNAME` | Registry username |
| `DOCKER_PASSWORD` | Registry access token |

### Jenkins (Required for push/deploy)

| Credential ID | Type | Purpose |
|---------------|------|----------|
| `docker-registry-url` | Secret text | Registry URL |
| `docker-registry-credentials` | Username/Password | Registry login |
| `deploy-dev-credentials` | SSH key | Dev deployment |
| `deploy-staging-credentials` | SSH key | Staging deployment |
| `deploy-prod-credentials` | SSH key | Prod deployment |

**📖 Full setup guide**: [SECRETS_SETUP.md](../SECRETS_SETUP.md)

---

## 🛡️ Security Scanning

Both pipelines include **Trivy** security scanning:

- Scans Docker image for vulnerabilities
- Reports HIGH and CRITICAL issues
- **GitHub Actions**: Uploads to Security tab (SARIF format)
- **Jenkins**: Displays in console output

**View results**:
- GitHub: Repository → Security → Code scanning alerts
- Jenkins: Build → Console Output

---

## 🎯 Pipeline Behavior

### Without Secrets Configured

| Pipeline | Tests | Build | Scan | Push | Deploy | Result |
|----------|-------|-------|------|------|--------|--------|
| **GitHub Actions** | ✅ | ✅ | ✅ | ⏭️ Skipped | N/A | ✅ **SUCCESS** |
| **Jenkins** | ✅ | ✅ | ✅ | ⚠️ Warning | ⚠️ Warning | ⚠️ **UNSTABLE** |

### With Secrets Configured

| Pipeline | Tests | Build | Scan | Push | Deploy | Result |
|----------|-------|-------|------|------|--------|--------|
| **GitHub Actions** | ✅ | ✅ | ✅ | ✅ | N/A | ✅ **SUCCESS** |
| **Jenkins** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ **SUCCESS** |

---

## 🐛 Troubleshooting

### GitHub Actions: "Username and password required"

**Symptom**: Push-to-registry job fails

**Cause**: Docker secrets not configured

**Fix**: Either:
1. Add secrets (see [SECRETS_SETUP.md](../SECRETS_SETUP.md)), OR
2. Ignore - job is optional, tests still pass

### GitHub Actions: "Resource not accessible by integration"

**Symptom**: SARIF upload fails

**Status**: ✅ **FIXED** - Added `permissions: security-events: write` + `continue-on-error: true`

### Jenkins: "Credentials not found"

**Symptom**: Push or Deploy stage fails

**Cause**: Jenkins credentials not configured

**Fix**: 
1. Check Jenkinsfile output for specific credential ID
2. Add credential in Jenkins (see [SECRETS_SETUP.md](../SECRETS_SETUP.md))
3. Re-run pipeline

### Jenkins: "Permission denied (publickey)"

**Symptom**: Deploy stage fails with SSH error

**Cause**: Invalid SSH key or wrong username

**Fix**:
1. Verify SSH credentials in Jenkins
2. Test manually: `ssh -i /path/to/key deploy@server.com`
3. Check `DEPLOY_HOST_DEV` environment variable

---

## 📊 Pipeline Features

### GitHub Actions

✅ **Included**:
- Parallel test execution (backend + frontend)
- Docker layer caching (via GitHub Actions cache)
- Security scanning with SARIF upload
- Smoke tests (health check + API test)
- Conditional registry push (main branch only)
- Fork-safe (no secrets required)

❌ **Not included**:
- Deployment (add as separate workflow if needed)
- Multi-stage builds (can be added)
- Matrix builds (can test multiple Java/Node versions)

### Jenkins

✅ **Included**:
- Sequential stages with test results
- JaCoCo coverage report (published as HTML)
- Docker-in-Docker agents for tests
- Trivy security scan
- Smoke tests
- Registry push (main branch)
- Multi-environment deployment (dev/staging/prod)
- Spring profile selection
- Error handling with UNSTABLE status

❌ **Not included**:
- Blue-Green deployment (can be added)
- Rollback mechanism (can be added)
- Database migrations (not needed for this app)

---

## 🔄 Workflow Comparison

| Feature | GitHub Actions | Jenkins |
|---------|----------------|----------|
| **Trigger** | Automatic (push/PR) | Manual or webhook |
| **Parallel Jobs** | ✅ Yes | ❌ Sequential |
| **Test Reports** | ✅ Built-in | ✅ Plugin required |
| **Security Scan** | ✅ SARIF upload | ✅ Console output |
| **Deployment** | ❌ Not configured | ✅ SSH-based |
| **Secrets Management** | GitHub Secrets | Jenkins Credentials |
| **Cost** | Free (public repos) | Self-hosted |
| **Setup Complexity** | Low (zero config) | Medium (requires Jenkins) |
| **Best For** | PR validation, CI | CD, multi-env deployment |

---

## 🚀 Next Steps

### For Development
1. Fork repository
2. Make changes
3. Push to your fork
4. GitHub Actions runs automatically ✅
5. Open PR to main repo

### For Production Deployment
1. Set up Jenkins server
2. Configure credentials ([SECRETS_SETUP.md](../SECRETS_SETUP.md))
3. Create pipeline job pointing to this repo
4. Configure deployment servers (dev/staging/prod)
5. Run pipeline on `main` branch
6. Image pushed + deployed automatically 🚀

### Alternative: GitHub Actions Deployment
To add deployment to GitHub Actions:
1. Create `.github/workflows/deploy.yml`
2. Add deployment secrets (SSH keys, server IPs)
3. Use `appleboy/ssh-action` for deployment
4. Trigger on `main` push after tests pass

---

## 📚 Related Documentation

- **[SECRETS_SETUP.md](../SECRETS_SETUP.md)** - Complete secrets configuration guide
- **[DEPLOYMENT.md](../DEPLOYMENT.md)** - Manual deployment instructions
- **[DOCKER_VERIFICATION.md](../DOCKER_VERIFICATION.md)** - Docker build verification checklist
- **[README.md](../README.md)** - Main project documentation

---

## ✅ Status Summary

### GitHub Actions (`.github/workflows/ci.yml`)
- ✅ Permissions fixed (`security-events: write` added)
- ✅ SARIF upload error handling (`continue-on-error: true`)
- ✅ Graceful secret handling (push job skips if secrets missing)
- ✅ Fork-safe (works on PRs from forks)
- ✅ **Ready to use!**

### Jenkins (`Jenkinsfile`)
- ✅ Error handling for missing credentials
- ✅ Helpful error messages with setup instructions
- ✅ Try-catch blocks for push and deploy stages
- ✅ UNSTABLE status instead of FAILURE when secrets missing
- ✅ **Ready to use!** (configure secrets for full functionality)

---

**Both pipelines are production-ready!** 🎉
