package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.repository.Repository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@FunctionalInterface
public interface IRepositoryFactory {

    IRepository create(final Repository entity, final ObjectMapper objectMapper) throws JsonProcessingException;

}
