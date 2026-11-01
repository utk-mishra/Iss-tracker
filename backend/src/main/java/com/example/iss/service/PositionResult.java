package com.example.iss.service;

import com.example.iss.domain.IssPosition;

public record PositionResult(IssPosition position, UpstreamMeta meta) {
}
