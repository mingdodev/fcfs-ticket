package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConcertService {
    private final ConcertRepository concertRepository;

    public List<Concert> getAllConcerts() {
        return concertRepository.findAll();
    }

    public Concert getConcertById(Long id) {
        return concertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 콘서트 정보를 찾을 수 없습니다."));
    }
}
