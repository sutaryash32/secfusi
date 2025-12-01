package com.secufusion.iam.controller;

import com.secufusion.iam.dto.GroupsDropdown;
import com.secufusion.iam.entity.Groups;
import com.secufusion.iam.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    // create a group which passed on req body and map those roles with groups with a set of roles endpoint here
    @PostMapping
    public ResponseEntity<Groups> createGroup(@RequestBody Groups groups){
        Groups savedGroup = groupService.createGroup(groups);
        return ResponseEntity.ok(savedGroup);
    }

    @GetMapping("/id")
    public ResponseEntity<Groups> getGroupById(@RequestParam String id){
        Groups group = groupService.getGroupById(id);
        return ResponseEntity.ok(group);
    }

    @GetMapping
    public ResponseEntity<List<Groups>> getAllGroups(){
        List<Groups> groups = groupService.getAllGroups();
        return ResponseEntity.ok(groups);
    }

    //dropdown endpoint for groups
    @GetMapping("/dropdown")
    public ResponseEntity<List<GroupsDropdown>> getGroupsForDropdown(){
        List<GroupsDropdown> dropdownList = groupService.getGroupsForDropdown();
        return ResponseEntity.ok(dropdownList);
    }
}
