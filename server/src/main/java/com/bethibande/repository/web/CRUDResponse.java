package com.bethibande.repository.web;

public sealed interface CRUDResponse<T> permits CRUDResponse.SuccessfulCRUDResponse, CRUDResponse.FailedCRUDResponse {

    static <T> CRUDResponse<T> success(final T value) {
        return new SuccessfulCRUDResponse<>(value);
    }

    static <T> CRUDResponse<T> failure(final String message, final String i18nKey) {
        return new FailedCRUDResponse<>(message, i18nKey);
    }

    final class SuccessfulCRUDResponse<T> implements CRUDResponse<T> {

        private final T value;

        private SuccessfulCRUDResponse(final T value) {
            this.value = value;
        }

        public T getValue() {
            return value;
        }
    }

    final class FailedCRUDResponse<T> implements CRUDResponse<T> {

        private final String message;
        private final String i18nKey;

        private FailedCRUDResponse(final String message, final String i18nKey) {
            this.message = message;
            this.i18nKey = i18nKey;
        }

        public String getMessage() {
            return message;
        }

        public String getI18nKey() {
            return i18nKey;
        }
    }

}
