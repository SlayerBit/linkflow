package com.linkflow.user.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for resolving role IDs to names and vice versa.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public Set<Long> resolveRoleIds(Set<String> roleNames) {
        return roleRepository.findByNameIn(roleNames).stream()
                .map(RoleEntity::getId)
                .collect(Collectors.toSet());
    }

    public Set<String> resolveRoleNames(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        return roleRepository.findByIdIn(roleIds).stream()
                .map(RoleEntity::getName)
                .collect(Collectors.toSet());
    }
}
