package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;

@Entity
@Table(name = "sub_categories")
public class SubCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Links back to the parent Category table
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    public SubCategory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}