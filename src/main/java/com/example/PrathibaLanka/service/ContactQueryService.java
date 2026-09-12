package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.QueryRequestDTO;
import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.enums.QueryStatus;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.ContactQueryRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ContactQueryService {

    private final ContactQueryRepository queryRepo;
    private final CustomerRepository customerRepo;
    private final AdminRepository adminRepo;
    private final EmailService emailService;

    /**
     * Step 1: Customer submits a query.
     * - Save query with status NEW.
     * - Send auto-response email.
     * - Mark autoResponseSent = true.
     */
    public ContactQuery submitQuery(QueryRequestDTO dto) {
        ContactQuery query = new ContactQuery();

        // If a customerId is provided, link it; otherwise leave null (guest query)
        if (dto.getCustomerId() != null) {
            Customer customer = customerRepo.findById(dto.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found with id: " + dto.getCustomerId()));
            query.setCustomer(customer);
        }

        query.setName(dto.getName());
        query.setEmail(dto.getEmail());
        query.setPhone(dto.getPhone());
        query.setSubject(dto.getSubject());
        query.setMessage(dto.getMessage());
        query.setStatus(QueryStatus.NEW);
        query.setAutoResponseSent(false);

        ContactQuery saved = queryRepo.save(query);

        // Send auto-response (and log it). The flag records the real outcome: it used to be set
        // to true even when the mail server rejected or was unreachable, which made the API lie.
        boolean autoResponseSent = emailService.sendAutoResponse(saved);
        saved.setAutoResponseSent(autoResponseSent);
        return queryRepo.save(saved);
    }

    /**
     * Step 2: Admin responds to a query.
     */
    public ContactQuery respondToQuery(Long queryId, String adminResponse, Long adminId) {
        ContactQuery query = queryRepo.findById(queryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Query not found with id: " + queryId));

        if (query.getStatus() == QueryStatus.RESPONDED) {
            throw new BadRequestException("This query has already been responded to.");
        }

        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Admin not found with id: " + adminId));

        query.setAdminResponse(adminResponse);
        query.setRespondedBy(admin);
        query.setStatus(QueryStatus.RESPONDED);
        query.setRespondedAt(LocalDateTime.now());

        return queryRepo.save(query);
    }

    /**
     * Utility: list all queries (for admin dashboard).
     */
    @Transactional(readOnly = true)
    public List<ContactQuery> getAllQueries() {
        return queryRepo.findAll();
    }

    /**
     * Utility: list only new (unresponded) queries.
     */
    @Transactional(readOnly = true)
    public List<ContactQuery> getNewQueries() {
        return queryRepo.findByStatus(QueryStatus.NEW);
    }
}