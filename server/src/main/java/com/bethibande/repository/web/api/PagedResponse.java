package com.bethibande.repository.web.api;

import java.util.List;

public record PagedResponse<T>(
        List<T> data,
        int page,
        int pages,
        int total
) {
}
