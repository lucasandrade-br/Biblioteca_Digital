package org.example.catalogo.service.mapper;

import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableMapper {
    private PageableMapper() {
    }

    public static Pageable toPageable(PageQuery query) {
        Sort sort = Sort.by(query.sort().stream()
            .map(order -> new Sort.Order(
                order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC,
                order.property()
            ))
            .toList());
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(query.pageNumber(), query.pageSize(), sort);
    }

    public static <T> PageResult<T> toResult(Page<T> page, PageQuery query) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), query.sort());
    }
}
