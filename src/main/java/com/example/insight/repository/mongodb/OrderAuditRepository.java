package com.example.insight.repository.mongodb;

import com.example.insight.model.mongodb.OrderAudit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderAuditRepository extends MongoRepository<OrderAudit, String> {
    
    // Retrieve historical audit logs sorted by timestamp
    List<OrderAudit> findByOrderIdOrderByTimestampDesc(Long orderId);
}
