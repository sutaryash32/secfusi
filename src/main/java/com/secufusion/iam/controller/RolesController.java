package com.secufusion.iam.controller;

import com.secufusion.iam.dto.RoleDropdownResponse;
import com.secufusion.iam.entity.Roles;
import com.secufusion.iam.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
public class RolesController {

    @Autowired
    private RoleService roleService;

    //create a role endpoint here
    @PostMapping
    public ResponseEntity<Roles> createRole(@RequestBody Roles roles) {
        Roles savedRole = roleService.createRoles(roles);
        return ResponseEntity.ok(savedRole);
    }

    @GetMapping
    public ResponseEntity<List<Roles>> getAllRoles() {
        List<Roles> rolesList = roleService.getAllRoles();
        return ResponseEntity.ok(rolesList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Roles> getRoleById(@PathVariable String id) {
        Roles role = roleService.getRoleById(id);
        return ResponseEntity.ok(role);
    }


    //give the roles as key value pair of name and id for dropdown
    @GetMapping("/dropdown")
    public ResponseEntity<List<RoleDropdownResponse>> getRolesForDropdown() {
        List<RoleDropdownResponse> dropdownList = roleService.getRolesForDropdown();
        return ResponseEntity.ok(dropdownList);
    }
}
