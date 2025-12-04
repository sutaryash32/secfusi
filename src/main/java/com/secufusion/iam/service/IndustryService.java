//package com.secufusion.iam.service;
//
//import com.secufusion.iam.dto.IndustryDTO;
//import com.secufusion.iam.repository.IndustryRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//@RequiredArgsConstructor
//public class IndustryService {
//
//    private final IndustryRepository industryRepository;
//
//    public List<IndustryDTO> getAllIndustries() {
//
//        return industryRepository.findAll()
//                .stream()
//                .map(industry -> new IndustryDTO(
//                        industry.getIndustryId(),
//                        industry.getIndustryName(),
//                        industry.getIndustryCode(),
//                        industry.getParentIndustry() != null ? industry.getParentIndustry().getIndustryId() : null,
//                        industry.getIsActive(),
//                        industry.getCreatedBy(),
//                        industry.getCreatedTimestamp(),
//                        industry.getLastModifiedBy(),
//                        industry.getLastModifiedTimestamp()
//                ))
//                .toList();
//    }
//}
