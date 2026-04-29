package com.example.cicd.customer;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Customer> findAll() {
        String sql = """
                SELECT id, full_name, email, created_at
                FROM customers
                ORDER BY id
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Customer(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                )
        );
    }
}
