package com.example.iss.service;

import java.util.List;

public record PassesResult(List<EnrichedPass> passes, PassesMeta passesMeta, UpstreamMeta cloudMeta) {
}
