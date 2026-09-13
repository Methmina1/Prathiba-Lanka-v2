package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.dto.request.QueryRequestDTO;
import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.ContactQuery;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.enums.QueryStatus;
import com.example.PrathibaLanka.event.QuerySubmittedEvent;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.ContactQueryRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

    /** Accepts guest queries too: customerId is optional. */
    public ContactQuery submitQuery(QueryRequestDTO dto) {
        ContactQuery query = new ContactQuery();

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

        // The auto-response is sent after this transaction commits, on the mail pool. The returned
        // query therefore still shows autoResponseSent = false; the mail worker flips it.
        events.publishEvent(new QuerySubmittedEvent(saved.getQueryId()));

        return saved;
    }

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

    @Transactional(readOnly = true)
    public List<ContactQuery> getAllQueries() {
        return queryRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<ContactQuery> getNewQueries() {
        return queryRepo.findByStatus(QueryStatus.NEW);
    }
}
