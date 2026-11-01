package com.example.iss.api;

import com.example.iss.api.dto.ErrorDto;
import com.example.iss.api.dto.LinksDto;
import com.example.iss.api.dto.MetaDto;
import com.example.iss.api.dto.OverviewResponseDto;
import com.example.iss.api.dto.PassDto;
import com.example.iss.api.dto.PassesMetaDto;
import com.example.iss.api.dto.PassesResponseDto;
import com.example.iss.api.dto.PositionDto;
import com.example.iss.api.dto.PositionResponseDto;
import com.example.iss.api.dto.UpstreamMetaDto;
import com.example.iss.domain.IssPosition;
import com.example.iss.domain.Pass;
import com.example.iss.service.EnrichedPass;
import com.example.iss.service.IssFacade;
import com.example.iss.service.IssOverview;
import com.example.iss.service.PassesResult;
import com.example.iss.service.PositionResult;
import com.example.iss.service.UpstreamMeta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST endpoints for ISS position/pass data (PROMPT.md section 6). Validation happens
 * here, before the facade is touched; the facade returns domain-shaped data only, this
 * controller maps it into the public DTO shape with HATEOAS links. lat/lon are never
 * logged at INFO level (Step 6 addition e) - no logging statement here references them.
 */
@RestController
@RequestMapping("/api/v1/iss")
public class IssController {

    private final IssFacade facade;

    public IssController(IssFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/overview")
    public OverviewResponseDto overview(@RequestParam String lat, @RequestParam String lon,
                                         @RequestParam(required = false, defaultValue = "false") String offline) {
        double validLat = validateLat(lat);
        double validLon = validateLon(lon);
        boolean isOffline = validateOffline(offline);

        IssOverview overview = facade.getOverview(validLat, validLon, isOffline);

        return new OverviewResponseDto(
                toPositionDto(overview.position()),
                overview.passes().stream().map(this::toPassDto).toList(),
                overview.nextVisiblePass() == null ? null : toPassDto(overview.nextVisiblePass()),
                overview.message(),
                overview.alerts(),
                new MetaDto(
                        toUpstreamMetaDto(overview.positionMeta()),
                        toPassesMetaDto(overview.passesMeta().meta(), overview.passesMeta().tleAgeHours(),
                                overview.passesMeta().tleStale()),
                        toUpstreamMetaDto(overview.cloudMeta())
                ),
                links("/api/v1/iss/overview")
        );
    }

    @GetMapping("/position")
    public PositionResponseDto position(@RequestParam(required = false, defaultValue = "false") String offline) {
        boolean isOffline = validateOffline(offline);
        PositionResult result = facade.getPosition(isOffline);
        return new PositionResponseDto(toPositionDto(result.position()), toUpstreamMetaDto(result.meta()),
                links("/api/v1/iss/position"));
    }

    @GetMapping("/passes")
    public PassesResponseDto passes(@RequestParam String lat, @RequestParam String lon,
                                     @RequestParam(required = false, defaultValue = "false") String offline) {
        double validLat = validateLat(lat);
        double validLon = validateLon(lon);
        boolean isOffline = validateOffline(offline);

        PassesResult result = facade.getPasses(validLat, validLon, isOffline);

        return new PassesResponseDto(
                result.passes().stream().map(this::toPassDto).toList(),
                new MetaDto(null,
                        toPassesMetaDto(result.passesMeta().meta(), result.passesMeta().tleAgeHours(),
                                result.passesMeta().tleStale()),
                        toUpstreamMetaDto(result.cloudMeta())),
                links("/api/v1/iss/passes")
        );
    }

    private double validateLat(String raw) {
        double value = parseFinite(raw, "lat");
        if (value < -90 || value > 90) {
            throw new InvalidLocationException("lat must be in [-90,90]");
        }
        return value;
    }

    private double validateLon(String raw) {
        double value = parseFinite(raw, "lon");
        if (value < -180 || value > 180) {
            throw new InvalidLocationException("lon must be in [-180,180]");
        }
        return value;
    }

    private double parseFinite(String raw, String fieldName) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidLocationException(fieldName + " must be a finite number");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new InvalidLocationException(fieldName + " must be a finite number");
        }
        return value;
    }

    private boolean validateOffline(String raw) {
        if ("true".equals(raw)) {
            return true;
        }
        if ("false".equals(raw)) {
            return false;
        }
        throw new InvalidLocationException("offline must be exactly 'true' or 'false'");
    }

    private PositionDto toPositionDto(IssPosition position) {
        return new PositionDto(
                position.latitude(), position.longitude(), position.altitudeKm(),
                position.velocityKmh(), position.observedAt().toString(), position.note()
        );
    }

    private PassDto toPassDto(EnrichedPass enrichedPass) {
        Pass pass = enrichedPass.pass();
        return new PassDto(
                pass.riseTime().toString(),
                pass.culminationTime().toString(),
                pass.setTime().toString(),
                pass.durationSec(),
                pass.riseDirection(),
                pass.riseAzimuthDeg(),
                pass.setDirection(),
                pass.setAzimuthDeg(),
                pass.maxElevationDeg(),
                pass.visible(),
                pass.isObservable(),
                pass.visibleStart() == null ? null : pass.visibleStart().toString(),
                pass.visibleEnd() == null ? null : pass.visibleEnd().toString(),
                pass.visibleDurationSec(),
                enrichedPass.magnitude(),
                enrichedPass.cloudCoverPct()
        );
    }

    private UpstreamMetaDto toUpstreamMetaDto(UpstreamMeta meta) {
        return new UpstreamMetaDto(meta.source().name(), meta.degraded(), meta.ageMs());
    }

    private PassesMetaDto toPassesMetaDto(UpstreamMeta meta, double tleAgeHours, boolean tleStale) {
        return new PassesMetaDto(toUpstreamMetaDto(meta), tleAgeHours, tleStale);
    }

    private LinksDto links(String self) {
        return new LinksDto(self, "/api/v1/iss/position", "/api/v1/iss/passes", "/actuator/health", "/docs");
    }
}
