package com.example.insight.model.mongodb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "order_audits")
public class OrderAudit {

    @Id
    private String id; // MongoDB ObjectId as String
    
    private Long orderId;
    private String action; // e.g., "ORDER_PLACED", "ORDER_PROCESSED"
    private LocalDateTime timestamp;
    private Map<String, Object> details; // flexible unstructured metadata
}
