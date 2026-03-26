package com.travel.agent.service.impl;

import com.travel.agent.dto.response.TripStatusResponse;
import com.travel.agent.service.TripProgressPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripProgressPushServiceImpl implements TripProgressPushService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void pushProgress(Long tripId, TripStatusResponse response) {
        String destination = "/topic/trip-progress/" + tripId;
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Pushed trip progress via WS: tripId={}, destination={}, progress={}, status={}",
                tripId,
                destination,
                response.getProgress(),
                response.getStatus());
    }
}
