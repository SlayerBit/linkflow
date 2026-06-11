package com.linkflow.user.infrastructure.adapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);
    List<RoleEntity> findByIdIn(Collection<Long> ids);
    List<RoleEntity> findByNameIn(Collection<String> names);
}
