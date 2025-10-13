package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public Member findByEmailOrThrow(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다: " + email));
    }

    public Member save(Member member) {
        return memberRepository.save(member);
    }

    public boolean existsByEmail(String email) {
        return memberRepository.findByEmail(email).isPresent();
    }
}
