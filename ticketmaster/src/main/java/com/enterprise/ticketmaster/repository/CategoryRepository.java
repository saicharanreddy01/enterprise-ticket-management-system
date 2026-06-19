package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Used to reject duplicate category names before insert
    boolean existsByName(String name);
}