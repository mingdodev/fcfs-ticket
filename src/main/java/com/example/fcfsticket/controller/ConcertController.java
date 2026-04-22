package com.example.fcfsticket.controller;

import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.dto.ConcertResponse;
import com.example.fcfsticket.service.ConcertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class ConcertController {
    private final ConcertService concertService;

    @GetMapping
    public ResponseEntity<ConcertListResponse> getConcerts() {
        List<Concert> concerts = concertService.getAllConcerts();
        List<ConcertResponse> responses = concerts.stream()
                .map(ConcertResponse::from)
                .toList();
        return ResponseEntity.ok(new ConcertListResponse(responses));
    }

    static class ConcertListResponse {
        public List<ConcertResponse> concerts;

        public ConcertListResponse(List<ConcertResponse> concerts) {
            this.concerts = concerts;
        }
    }
}
