package com.template.OAuth.controller;

import com.template.OAuth.annotation.Auditable;
import com.template.OAuth.dto.UserDto;
import com.template.OAuth.entities.User;
import com.template.OAuth.enums.AuditEventType;
import com.template.OAuth.enums.Role;
import com.template.OAuth.service.MessageService;
import com.template.OAuth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
// Change from "/api" to "/api/admin" for admin-specific endpoints
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@Tag(name = "Admin", description = "Admin management endpoints")
@SecurityRequirement(name = "cookie") // Require authentication for all admin endpoints
public class AdminController {

    private final UserService userService;
    private final MessageService messageService;

    @Autowired
    public AdminController(UserService userService, MessageService messageService) {
        this.userService = userService;
        this.messageService = messageService;
    }

    @Operation(summary = "Assign role to user",
            description = "Assigns a specific role to a user (Admin access required)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role assigned successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not authorized",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign-role")
    @Auditable(type = AuditEventType.USER_ROLE_CHANGED, description = "Admin assigned role to user", includeArgs = true)
    public ResponseEntity<Map<String, String>> assignRole(
            @Parameter(description = "User email", required = true)
            @RequestParam String email,
            @Parameter(description = "Role to assign", required = true,
                    schema = @Schema(implementation = Role.class))
            @RequestParam Role role) {
        userService.assignRole(email, role);

        Map<String, String> response = new HashMap<>();
        response.put("message", messageService.getMessage("user.role.assigned"));
        response.put("email", email);
        response.put("role", role.name());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Remove role from user",
            description = "Removes a specific role from a user (Admin access required)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role removed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not authorized",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content)
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/remove-role")
    @Auditable(type = AuditEventType.USER_ROLE_CHANGED, description = "Admin removed role from user", includeArgs = true)
    public ResponseEntity<Map<String, String>> removeRole(
            @Parameter(description = "User email", required = true)
            @RequestParam String email,
            @Parameter(description = "Role to remove", required = true,
                    schema = @Schema(implementation = Role.class))
            @RequestParam Role role) {
        userService.removeRole(email, role);

        Map<String, String> response = new HashMap<>();
        response.put("message", messageService.getMessage("user.role.removed"));
        response.put("email", email);
        response.put("role", role.name());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all users",
            description = "Retrieves all users in the system (Admin or Moderator access required)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not authenticated",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not authorized",
                    content = @Content)
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<User> users = userService.findAllUsers();
        List<UserDto> userDtos = users.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(userDtos);
    }

    @Operation(summary = "Get user profile",
            description = "Retrieves the profile of the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Not authenticated",
                    content = @Content)
    })
    @GetMapping("/profile")
    public ResponseEntity<UserDto> getUserProfile() {
        User currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(convertToDto(currentUser));
    }

    private UserDto convertToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPicture(user.getPicture());
        dto.setRoles(user.getRoles());
        return dto;
    }
}