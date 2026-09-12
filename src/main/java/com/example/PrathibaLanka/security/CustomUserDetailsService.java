package com.example.PrathibaLanka.security;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.Customer;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim();

        Optional<Admin> adminOpt = adminRepository.findByEmailIgnoreCase(normalized);
        if (adminOpt.isPresent()) {
            return UserPrincipal.fromAdmin(adminOpt.get());
        }

        Optional<Customer> customerOpt = customerRepository.findByEmailIgnoreCase(normalized);
        if (customerOpt.isPresent()) {
            return UserPrincipal.fromCustomer(customerOpt.get());
        }

        throw new UsernameNotFoundException("No user found with email: " + email);
    }
}