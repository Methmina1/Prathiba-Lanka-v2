package com.example.PrathibaLanka.service;

import  com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.EmailLog;
import com.example.PrathibaLanka.enums.EmailType;
import com.example.PrathibaLanka.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    private final EmailLogRepository emailLogRepository;

    // Initial method for sending emails
    private void sendAndLog(String to, String subject, String body, EmailType type, BookingRequest booking){
        boolean sent = false;
        try{
            if(mailSender != null){
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                sent = true;
                log.info("Email sent to {} with the subject: {}", to, subject);
            }else{
                log.warn("Error in Mail sender configuration! Email not send");
            }
        }catch (Exception e){
            log.warn("Failed to sent the email to {}: {}", to, e.getMessage());
        }

        //Each attempt at sending an email is logged (regardless of send or not)
        // TODO: make it a downloadable svg or something in the future
        EmailLog emailLog = new EmailLog();
        emailLog.setBookingRequest(booking);
        emailLog.setEmailType(type);
        emailLog.setRecipientEmail(to);
        emailLog.setSubject(subject);
        emailLog.setBody(body);
        emailLog.setSentAt(LocalDateTime.now());
        emailLog.setSent(sent);
        emailLogRepository.save(emailLog);
    }

    public void sendBookingPendingEmail(BookingRequest booking) {
        String to = booking.getCustomer().getEmail();
        String subject = "Your Trip Booking is Pending – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getCustomer().getFullName() + ",\n\n"
                + "Thank you for your booking request.\n"
                + "Your booking is currently pending. A consultant will contact you soon.\n"
                + "Your PIN: " + booking.getPinCode() + "\n\n"
                + "You can track your booking status using this PIN on our website.\n\n"
                + "Best regards,\nPrathibaLanka Team";

        sendAndLog(to, subject, body, EmailType.PENDING_NOTIFICATION, booking);
    }

    public void sendBookingConfirmedEmail(BookingRequest booking) {
        String to = booking.getCustomer().getEmail();
        String subject = "Your Trip is Confirmed! – PIN: " + booking.getPinCode();
        String body = "Dear " + booking.getCustomer().getFullName() + ",\n\n"
                + "Congratulations! Your trip has been confirmed.\n"
                + "PIN: " + booking.getPinCode() + "\n"
                + "Confirmed Date: " + booking.getConfirmedDate() + "\n"
                + "Total Price: $" + booking.getConfirmedPrice() + "\n\n"
                + "We look forward to serving you.\n\n"
                + "Best regards,\nPrathibaLanka Team";

        sendAndLog(to, subject, body, EmailType.CONFIRMATION, booking);
    }

    public void sendAutoResponse(ContactQuery query) {
        String to = query.getEmail();
        String subject = "We received your message – PrathibaLanka";
        String body = "Dear " + query.getName() + ",\n\n"
                + "Thank you for contacting us.\n"
                + "We have received your message and will get back to you as soon as possible.\n\n"
                + "Your query reference: " + query.getQueryId() + "\n\n"
                + "Best regards,\nPrathibaLanka Team";

        sendAndLog(to, subject, body, EmailType.AUTO_RESPONSE, null);
    }
}