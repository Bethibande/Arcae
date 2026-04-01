package com.bethibande.repository.web.api;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
        @NotNull List<T> data,
        int page,
        int pages,
        int total
) {
    public static <T, R> PagedResponse<R> from(final PanacheQuery<T> query, final int page, final Function<T, R> from) {
        final int total = (int) query.count();
        final int totalPages = (int) Math.ceil(total / (double) query.page().size);

        return new PagedResponse<>(
                query.list().stream().map(from).toList(),
                page,
                totalPages,
                total
        );
    }
}
