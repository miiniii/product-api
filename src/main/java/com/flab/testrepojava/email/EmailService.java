package com.flab.testrepojava.email;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendParticipationSuccess(Long userId, Long productId) {

        String to = "miiniiiiii9@gmail.com"; // 유저 이메일 (예: userService.getEmail(userId))

        String subject = "[이벤트 참여 성공 안내]";
        String text = String.format("🎉 유저 %d님, 상품 %d 이벤트에 성공하셨습니다!", userId, productId);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("✅ 이메일 전송 완료 → {}", to);
        } catch (Exception e) {
            log.error("❌ 이메일 전송 실패", e);
        }
    }
}
