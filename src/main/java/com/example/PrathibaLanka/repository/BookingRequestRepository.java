package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.BookingRequest;
import com.example.PrathibaLanka.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {
    Optional<BookingRequest> findByPinCode(String pinCode);
    List<BookingRequest> findByStatus(BookingStatus status);
}