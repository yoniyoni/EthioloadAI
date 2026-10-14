package com.ethioloadai.carrier.team;
import com.ethioloadai.carrier.company.dto.CarrierCompanyRequest;
import com.ethioloadai.carrier.company.entity.*;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.service.CarrierCompanyService;
import com.ethioloadai.carrier.team.dto.*;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.repository.*;
import com.ethioloadai.carrier.team.service.*;
import com.ethioloadai.carrier.team.config.InvitationProperties;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.email.EmailService;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"carrier.invitations.cooldown-seconds=0","carrier.invitations.max-per-hour=100"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
abstract class TeamTestSupport {
 @Autowired CompanyInvitationService invitations;
 @Autowired CompanyMembershipService teams;
 @Autowired CompanyMembershipRepository memberships;
 @Autowired CompanyInvitationRepository invitationRepository;
 @Autowired CarrierCompanyRepository companies;
 @Autowired CarrierCompanyService companyService;
 @Autowired UserRepository users;
 @Autowired JwtService jwt;
 @Autowired ObjectMapper json;
 @Autowired MockMvc mvc;
 @Autowired InvitationTokens tokens;
 @Autowired InvitationProperties properties;
 @Autowired EntityManager em;
 @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager transactions;
 @MockBean EmailService email;
 @MockBean DocumentStorage storage;
 User owner, employee, stranger, admin;
 Long companyId;
 final Map<String,String> links = new ConcurrentHashMap<>();
 @BeforeEach void initTeam() {
  properties.setCooldownSeconds(0);
  properties.setMaxPerHour(100);
  owner = user(User.Role.SHIPPER);
  employee = user(User.Role.SHIPPER);
  stranger = user(User.Role.FLEET_OWNER);
  admin = user(User.Role.ADMIN);
  companyId = company(owner, true);
  links.clear();
  doAnswer(call -> { links.put(call.getArgument(0), call.getArgument(3)); return null; })
    .when(email).sendInvitation(anyString(), anyString(), any(), anyString(), any());
 }
 User user(User.Role role) {
  return users.saveAndFlush(User.builder().fullName("Team test").phone(UUID.randomUUID().toString())
   .email(UUID.randomUUID()+"@example.test").password("unused").role(role).isActive(true).build());
 }
 Long company(User creator, boolean approved) {
  var request = new CarrierCompanyRequest();
  request.setCompanyName("Example Logistics");
  Long id = companyService.create(request, creator).getId();
  if (approved) {
   var c = companies.findById(id).orElseThrow();
   c.setVerificationStatus(VerificationStatus.APPROVED);
   c.setSubmittedAt(LocalDateTime.now());
   c.setReviewedAt(LocalDateTime.now());
   c.setReviewedBy(admin.getId());
   companies.saveAndFlush(c);
  }
  return id;
 }
 InvitationRequest request(String address, CompanyRole role) {
  var req = new InvitationRequest(); req.setEmail(address); req.setRole(role); return req;
 }
 InvitationResponse invite(User target, CompanyRole role) { return invitations.invite(companyId, request(target.getEmail(),role), owner); }
 String token(User target) {
  String link = links.get(target.getEmail().strip().toLowerCase(Locale.ROOT));
  return link.substring(link.indexOf("#token=")+7);
 }
 MembershipResponse join() { invite(employee,CompanyRole.FLEET_MANAGER); return invitations.accept(token(employee),employee); }
 String auth(User user) { return "Bearer "+jwt.generateToken(user); }
 String team() { return "/carrier-companies/"+companyId+"/team"; }
 String body(Object obj) throws Exception { return json.writeValueAsString(obj); }
 Map<String,String> tokenBody(String token) { return Map.of("token",token); }
}

