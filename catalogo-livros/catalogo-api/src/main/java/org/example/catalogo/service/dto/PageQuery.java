package org.example.catalogo.service.dto;

import java.util.List;

public record PageQuery(int pageNumber, int pageSize, List<SortOrder> sort) {
    public PageQuery {
        sort = List.copyOf(sort);
    }
}
