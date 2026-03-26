package com.travel.agent.service;

import com.travel.agent.dto.response.TripStatusResponse;

public interface TripProgressPushService {
    void pushProgress(Long tripId, TripStatusResponse response);
}
