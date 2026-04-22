package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.repository.ConcertRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    ConcertRepository concertRepository;

    @InjectMocks
    ConcertService concertService;

    @Test
    void 콘서트_목록을_반환한다() {
        List<Concert> concerts = List.of(
                Concert.builder().id(1L).name("콘서트 A").remainingTickets(1000).build(),
                Concert.builder().id(2L).name("콘서트 B").remainingTickets(500).build()
        );
        given(concertRepository.findAll()).willReturn(concerts);

        List<Concert> result = concertService.getAllConcerts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("콘서트 A");
    }

    @Test
    void ID로_콘서트를_반환한다() {
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(1000).build();
        given(concertRepository.findById(1L)).willReturn(Optional.of(concert));

        Concert result = concertService.getConcertById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_ID_조회시_예외를_던진다() {
        given(concertRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> concertService.getConcertById(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
