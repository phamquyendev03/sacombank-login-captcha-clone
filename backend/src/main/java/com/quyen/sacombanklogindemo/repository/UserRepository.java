package com.quyen.sacombanklogindemo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.quyen.sacombanklogindemo.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);
}
