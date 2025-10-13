package com.flab.testrepojava.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "members", indexes = {
        @Index(name = "idx_member_email", columnList = "email", unique = true)
})
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email; // 로그인 ID로도 사용됨

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;


    private boolean active; // 활성화 여부

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // == Spring Security 필수 구현 메서드 == //
//    @Override
//    public Collection<? extends GrantedAuthority> getAuthorities() {
//        return Collections.singleton(() -> "ROLE_" + this.role.name());
//    }
//
//    @Override
//    public String getUsername() {
//        return this.email; // 로그인 ID
//    }
//
//    @Override
//    public boolean isAccountNonExpired() {
//        return true; // 계정 만료 정책 없음
//    }
//
//    @Override
//    public boolean isAccountNonLocked() {
//        return true; // 잠금 정책 없음
//    }
//
//    @Override
//    public boolean isCredentialsNonExpired() {
//        return true; // 비밀번호 만료 정책 없음
//    }
//
//    @Override
//    public boolean isEnabled() {
//        return this.active;
//    }
}
