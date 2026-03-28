package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MemberServiceTest {

    private MemberRepository memberRepository;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        memberService = new MemberService(memberRepository);
    }

    @Test
    @DisplayName("findByEmailOrThorw - 이메일에 해당하는 회원이 있으면 반환")
    void findByEmailOrThrow_success() {
        //given
        String email = "test@test.com";
        Member member = new Member();

        when(memberRepository.findByEmail(email)).thenReturn(Optional.of(member));

        //when
        Member result = memberService.findByEmailOrThrow(email);

        //then
        assertThat(result).isEqualTo(member);
        verify(memberRepository).findByEmail(email);
    }

    @Test
    @DisplayName("findByEmailOrThrow - 이메일에 해당하는 회원이 없으면 예외 발생")
    void findByEmailOrThrow_fail() {
        // given
        String email = "test@test.com";

        when(memberRepository.findByEmail(email)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.findByEmailOrThrow(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 사용자입니다: " + email);

        verify(memberRepository).findByEmail(email);
    }

}