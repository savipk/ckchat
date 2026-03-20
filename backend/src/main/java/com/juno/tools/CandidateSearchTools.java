package com.juno.tools;

import com.juno.service.EmployeeDirectoryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Candidate Search Agent tools.
 * Ported from Juno agents/candidate_search/tools/.
 */
@Component
public class CandidateSearchTools {

    private final EmployeeDirectoryService employeeDirectory;

    public CandidateSearchTools(EmployeeDirectoryService employeeDirectory) {
        this.employeeDirectory = employeeDirectory;
    }

    @Tool(description = "Search for internal employees/candidates by skills, level, location, and department. "
            + "Results shown as candidate cards — do NOT list them in chat.")
    public Map<String, Object> searchCandidates(
            @ToolParam(description = "Search text across employee fields", required = false) String searchText,
            @ToolParam(description = "Filters: location, level/rank, department, skills (list), yearsAtCompany",
                    required = false) Map<String, Object> filters,
            @ToolParam(description = "Pagination offset", required = false) Integer offset,
            @ToolParam(description = "Max results per page (default 3)", required = false) Integer topK) {
        return employeeDirectory.searchCandidates(
                searchText,
                filters,
                offset != null ? offset : 0,
                topK != null ? topK : 3);
    }

    @Tool(description = "View detailed profile of a specific candidate/employee.")
    public Map<String, Object> viewCandidate(
            @ToolParam(description = "Employee ID") String employeeId) {
        var employee = employeeDirectory.getEmployeeById(employeeId);
        if (employee == null) {
            return Map.of("error", "Employee not found: " + employeeId);
        }
        return employee;
    }
}
