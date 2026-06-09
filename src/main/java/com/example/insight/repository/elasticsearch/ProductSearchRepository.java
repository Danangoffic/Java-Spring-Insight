package com.example.insight.repository.elasticsearch;

import com.example.insight.model.elasticsearch.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    // Simpler search: match name or category (Elasticsearch handles tokenization automatically)
    List<ProductDocument> findByNameContainingOrCategoryContaining(String name, String category);
}
