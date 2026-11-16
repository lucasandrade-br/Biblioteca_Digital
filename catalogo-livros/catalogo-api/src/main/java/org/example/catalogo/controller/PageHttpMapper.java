package org.example.catalogo.controller;

import org.example.catalogo.service.dto.PageQuery;
import org.example.catalogo.service.dto.PageResult;
import org.example.catalogo.service.dto.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;

final class PageHttpMapper {

    private PageHttpMapper() {
    }

    static PageQuery toQuery(Pageable pageable) {
        List<SortOrder> sort = pageable.getSort().stream()
            .map(order -> new SortOrder(order.getProperty(), order.isAscending()))
            .toList();
        return new PageQuery(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    static <T, R> Page<R> toPage(PageResult<T> result, Function<T, R> mapper) {
        Sort sort = Sort.by(result.sort().stream()
            .map(order -> new Sort.Order(
                order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC,
                order.property()
            ))
            .toList());
        Pageable pageable = PageRequest.of(result.pageNumber(), result.pageSize(), sort);
        return new PageImpl<>(result.content().stream().map(mapper).toList(), pageable, result.totalElements());
    }
}
