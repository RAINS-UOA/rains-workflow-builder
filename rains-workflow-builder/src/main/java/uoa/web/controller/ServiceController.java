package uoa.web.controller;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.solr.common.util.Base64;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.google.gson.Gson;

import uoa.init.graphdb.ConnectionFactory;
import uoa.init.graphdb.GraphDBUtils;
import uoa.model.components.NewSystemForm;
import uoa.model.components.SystemDetails;
import uoa.web.handlers.SystemRecordManager;
import uoa.web.storage.AuthorisationCacheStorage;
import uoa.web.storage.FileUploadStorageFileNotFoundException;
import uoa.web.storage.FileUploadStorageService;

@Controller
public class ServiceController {
	Repository repository = GraphDBUtils.getFabricRepository(GraphDBUtils.getRepositoryManager());
	
	private ObjectPool<RepositoryConnection>  connectionPool = new GenericObjectPool<RepositoryConnection>(new ConnectionFactory(repository));
	
	private final FileUploadStorageService storageService;

	@Autowired
	public ServiceController(FileUploadStorageService storageService) {
		this.storageService = storageService;
	}

	@GetMapping("/upload")
	public String listUploadedFiles(Model model) throws IOException {

		model.addAttribute("files", storageService.loadAll().map(
				path -> MvcUriComponentsBuilder.fromMethodName(ServiceController.class,
						"serveFile", path.getFileName().toString()).build().toUri().toString())
				.collect(Collectors.toList()));

		return "uploadForm";
	}

	@GetMapping("/files/{filename:.+}")
	@ResponseBody
	public ResponseEntity<Resource> serveFile(@PathVariable String filename) {

		Resource file = storageService.loadAsResource(filename);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"" + file.getFilename() + "\"").body(file);
	}

	@PostMapping("/upload")
	@ResponseBody
	public String handleFileUpload(@RequestParam("file") MultipartFile file,
			RedirectAttributes redirectAttributes) throws NoSuchElementException, IllegalStateException, Exception {
        String response = "";     
		if (file.isEmpty() ) {
			response = "you didn.t provide a file";
             }
		else {
		  //  storageService.store(file);
		    SystemRecordManager manager = new SystemRecordManager(connectionPool);
		    manager.saveUploadToWorkflowComponentLibrary (file);
		    manager.shutdown();
		 
		
		redirectAttributes.addFlashAttribute("message",
				"You successfully uploaded " + file.getOriginalFilename() + "!");
		
		response = "ok";
		}
		return response;
	}

	@ExceptionHandler(FileUploadStorageFileNotFoundException.class)
	public ResponseEntity<?> handleStorageFileNotFound(FileUploadStorageFileNotFoundException exc) {
		return ResponseEntity.notFound().build();
	}
	
	
	@GetMapping("/")
	public String index(Model model) {
		
		return "index";
	}
	
	@GetMapping("/home")
	public String home( Model model) {
		return "home";
	}
	
	@GetMapping("/workflowBuilderStageSelector")
	public String home() {
		return "workflowBuilderStageSelector";
	}
	
	@GetMapping("/workflowBuilder")
	public String planDesigner( @RequestParam String mode, @RequestParam String stage, Model model) {
		model.addAttribute("stage", stage);
		model.addAttribute("mode", mode);
		return "planDesigner";
	}
	/*
	@GetMapping("/testJSONLD")
	public String savePlanFromJSONLD () throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		manager.savePlanFromJSONLD();
		return "home";
	}
	*/
	
	@GetMapping("/provenanceCollector")
	public String provenanceCollector( @RequestParam String mode, @RequestParam String stage, Model model) {
		model.addAttribute("stage", stage);
		model.addAttribute("mode", mode);
		return "provenanceCollector";
	}
	
	@GetMapping("/auditManager")
	public String auditManager(  Model model) {
		
		return "auditManager";
	}
	
	
	@PostMapping("/saveTemplatePlan")
	@ResponseBody
	public String saveTemplatePlan (@RequestBody String payload) throws NoSuchElementException, IllegalStateException, Exception  {
		System.out.println(payload);
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		manager.saveTemplatePlanFromJSONLD(payload);
		manager.shutdown();
		return "Request to Save Template Processed - To Do work on the message to confirm it saved sucesfully";
	}
	
	@PostMapping("/savePlan")
	@ResponseBody
	public String savePlan (@RequestParam MultiValueMap<String,String> paramMap) throws NoSuchElementException, IllegalStateException, Exception  {
		System.out.println(paramMap);
		SystemRecordManager manager = new SystemRecordManager(connectionPool);

		manager.savePlanFromJSONLD(paramMap.getFirst("systemIri"),paramMap.getFirst("payload"));
		manager.shutdown();
		return "{\"result\":\"Received\"}";
	}
	
	
	@GetMapping("/getTemplatePlans")
	@ResponseBody
	public String getTemplatePlans () throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		String jsonResult = manager.getTemplatePlans();
		manager.shutdown();
		return jsonResult;
	}
	
	@GetMapping("/getSavedPlanForEachStage")
	@ResponseBody
	public String getSavedPlanForEachStage (@RequestParam String systemIri) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		ArrayList <HashMap <String,String >> list = manager.getSavedPlanForEachStage(systemIri);
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(list);
	}
	
	@GetMapping("/getTemplate")
	@ResponseBody
	public String getTemplate (@RequestParam String planIri) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		String jsonResult = manager.getTemplate(planIri);
		manager.shutdown();
		return jsonResult;
	}
	
	@GetMapping("/getPlansNamedGraph")
	@ResponseBody
	public String getPlansNamedGraph (@RequestParam String systemIri) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		String result = manager.getPlansNamedGraph(systemIri);
		manager.shutdown();
		HashMap <String,String > map = new  HashMap <String,String >  ();
		map.put("result", result);
		Gson gson = new Gson(); 
		return gson.toJson(map);
	}
	/*
	@GetMapping("/getTopLevelPlan")
	@ResponseBody
	public String getTopLevelPlan (@RequestParam String systemIri) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		HashMap<String, String> map = manager.getTopLevelPlan(systemIri);
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(map);
	}
	*/
	
	
	@GetMapping("/getStagePlanIRI")
	@ResponseBody
	public String getTopLevelPlan (@RequestParam String systemIri ,@RequestParam String stage) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		HashMap<String, String> map = manager.getStagePlanIRI(systemIri, stage);
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(map);
	}
	
	
	@GetMapping("/getStepComponentHierarchy")
	@ResponseBody
	public String getStepComponentHierarchy () throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		HashMap <String,HashSet <String >> map = manager.getStepComponentHierarchy();
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(map);
	}
	
	@GetMapping("/getVariableComponentHierarchy")
	@ResponseBody
	public String getVariableComponentHierarchy () throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		HashMap <String,HashSet <String >> map = manager.getVariableComponentHierarchy();
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(map);
	}
	
	
	
	@GetMapping("/getSavedPlan")
	@ResponseBody
	public String getSavedPlan (@RequestParam String systemIri,@RequestParam String stage) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		String jsonResult = manager.getSavedPlan (stage,  systemIri);
		manager.shutdown();
		return jsonResult;
		
	}
	
	
	
	@GetMapping("/components")
	public String components () throws NoSuchElementException, IllegalStateException, Exception  {
		//SystemRecordManager manager = new SystemRecordManager(connectionPool);
		//manager.shutdown();	
		return "componentsLibrary";
	}
	
	@GetMapping("/system")
	public String systemDetails(@RequestParam String iri, Model model) throws NoSuchElementException, IllegalStateException, Exception {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		
		SystemDetails system = manager.getSystemDetails (iri);
		
		
		model.addAttribute("systemDetails", system);
		manager.shutdown();
		
		
		return "systemDetails";
	}
	
	@GetMapping("/systems")
	public String systemLibrary(Model model) throws NoSuchElementException, IllegalStateException, Exception {		
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		model.addAttribute("systems", manager.getStoredSystemsIRI());
		manager.shutdown();
		return "systemLibrary";
	}
	
	@PostMapping("/systems")
	public String systemLibraryNewSystemAdded(@ModelAttribute NewSystemForm newSystem) throws NoSuchElementException, IllegalStateException, Exception {
		
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		manager.addSystemInfo(newSystem); 
		//System.out.println(newSystem.getIri());
		manager.shutdown();
		//System.out.println(manager.getStoredSystemsIRI());
		return "index";
		//return "redirect:/systems";
	}
	

	@GetMapping("/createSystemForm")
	public String addSystem(Model model) {
		model.addAttribute("newSystem", new NewSystemForm());
		return "createSystemForm";
	}
	
	
	@GetMapping("/getAllowedVariableTypesForStepType")
	@ResponseBody
	public String getAllowedVariableTypesForStepType(@RequestParam String stepTypeIRI, @RequestParam String restrictionPropertyIRI) throws NoSuchElementException, IllegalStateException, Exception {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		Gson gson = new Gson();
		//System.out.println(new String(Base64.base64ToByteArray(stepTypeIRI)));
		//System.out.println(new String(Base64.base64ToByteArray(restrictionPropertyIRI)));
		return gson.toJson(manager.getAllowedVariableTypesForStepType(new String(Base64.base64ToByteArray(stepTypeIRI)),new String(Base64.base64ToByteArray(restrictionPropertyIRI))));
	}
	
	
	
	@GetMapping("/getAllowedInformationElelementForInformationRealizationType")
	@ResponseBody
	public String getAllowedInformationElelementForInformationRealizationType(@RequestParam String informationRealizationType) throws NoSuchElementException, IllegalStateException, Exception {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		Gson gson = new Gson();
		//System.out.println(new String(Base64.base64ToByteArray(stepTypeIRI)));
		//System.out.println(new String(Base64.base64ToByteArray(restrictionPropertyIRI)));
		return gson.toJson(manager.getAllowedInformationElelementForInformationRealizationType(new String(Base64.base64ToByteArray(informationRealizationType))));
	}
	@GetMapping("/createHumanProvenaceGenerationTask")
	@ResponseBody
	public String createHumanProvenaceGenerationTask (@RequestParam String planIRI,@RequestParam String systemIRI) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		AuthorisationCacheStorage.createHumanProvenaceGenerationTask(new String(Base64.base64ToByteArray(planIRI)), manager.createNewExecutionBundle(new String(Base64.base64ToByteArray(planIRI)), new String(Base64.base64ToByteArray(systemIRI))));
		//to do addd split based on agents associated with steps
		
		manager.shutdown();
		return "createHumanProvenaceGenerationTask: to do some meaningful response";
		
	}
	
	@GetMapping("/getHumanProvenaceGenerationTasksForPlan")
	@ResponseBody
	public String getHumanProvenaceGenerationTasksForPlan (@RequestParam String planIRI) throws NoSuchElementException, IllegalStateException, Exception  {
		Gson gson = new Gson(); 
		return gson.toJson(AuthorisationCacheStorage.getHumanProvenaceGenerationTasksForPlan(new String(Base64.base64ToByteArray(planIRI))));
	}
	
	
	@GetMapping("/getPlanStructureForHumanForm")
	@ResponseBody
	public String createProvenanceTraceHumanInterface (@RequestParam String token) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		String jsonld = manager.createProvenanceTraceHumanInterface (token);
		manager.shutdown();
		return jsonld;
	}
	
	@GetMapping("/createProvenanceTrace")
	public String createProvenanceTrace (@RequestParam String token) throws NoSuchElementException, IllegalStateException, Exception  {
		//TO- DO check the token and see if the form has already been completed 
		
		return "provenanceTraceForm";
	}
	
	
	
	@PostMapping("/uploadHumanTaskProvenanceTrace")
	@ResponseBody
	public String uploadHumanTaskProvenanceTrace (@RequestParam String payload, @RequestParam String token) throws NoSuchElementException, IllegalStateException, Exception  {
		//to do need to check token again
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		System.out.println("Creating trace");
		String response = manager.saveHumanTaskProvenanceTrace(payload,token);
		manager.shutdown();
		return response;
	}
	
	
	@GetMapping("/getAgentsInExecutionTraces")
	@ResponseBody
	public String getAgentsInExecutionTraces (@RequestParam String systemIRI) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		HashSet hashSet = manager.getAgentsInExecutionTraces (systemIRI);
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(hashSet);
	}
	
	@GetMapping("/getAgentsParticipationDetailsInExecutionTraces")
	@ResponseBody
	public String getAgentsInExecutionTraces (@RequestParam String systemIRI,@RequestParam String agentIRI ) throws NoSuchElementException, IllegalStateException, Exception  {
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		ArrayList <HashMap> list = manager.getAgentsParticipationDetailsInExecutionTraces (systemIRI, agentIRI);
		manager.shutdown();
		Gson gson = new Gson(); 
		return gson.toJson(list);
	}
	
	@GetMapping("/getActivityDetailsInExecutionTraces")
	@ResponseBody
	public String getActivityDetailsInExecutionTraces (@RequestParam String systemIRI,@RequestParam String activityIRI ) throws NoSuchElementException, IllegalStateException, Exception  {
	SystemRecordManager manager = new SystemRecordManager(connectionPool);
	ArrayList <HashMap> list = manager.getActivityDetailsInExecutionTraces( systemIRI,  activityIRI);
	manager.shutdown();
	Gson gson = new Gson(); 
	return gson.toJson(list);
}
	
	@GetMapping("/getOutputDetailsInExecutionTraces")
	@ResponseBody
	public String getOutputDetailsInExecutionTraces (@RequestParam String systemIRI,@RequestParam String infoRealizationIRI ) throws NoSuchElementException, IllegalStateException, Exception  {
	SystemRecordManager manager = new SystemRecordManager(connectionPool);
	ArrayList <HashMap> list = manager.getOutputDetailsInExecutionTraces( systemIRI,  infoRealizationIRI);
	manager.shutdown();
	Gson gson = new Gson(); 
	return gson.toJson(list);
}
	
	@GetMapping("/getDependingActivities")
	@ResponseBody
	public String getDependingActivities (@RequestParam String systemIRI,@RequestParam String entityIRI ) throws NoSuchElementException, IllegalStateException, Exception  {
	SystemRecordManager manager = new SystemRecordManager(connectionPool);
	ArrayList <HashMap> list = manager.getDependingActivities( systemIRI,  entityIRI);
	manager.shutdown();
	Gson gson = new Gson(); 
	return gson.toJson(list);
	}
	
	@GetMapping("/getAccountableObjects")
	@ResponseBody
	public String getAccountableObjects (@RequestParam String systemIRI ) throws NoSuchElementException, IllegalStateException, Exception  {
	
		SystemRecordManager manager = new SystemRecordManager(connectionPool);
		HashMap <String,HashMap <String,String >> list = manager.getAccountableObjects( systemIRI);
	manager.shutdown();
	Gson gson = new Gson(); 
	return gson.toJson(list);
	
	}
	
	@PostMapping("/saveAccountableObject")
	@ResponseBody
	public String saveAccountableObject (@RequestParam MultiValueMap<String,String> paramMap) throws NoSuchElementException, IllegalStateException, Exception  {
		System.out.println(paramMap);
		SystemRecordManager manager = new SystemRecordManager(connectionPool);

		manager.saveAccountableObject(paramMap.getFirst("payload"));
		manager.shutdown();
		return "{\"result\":\"Received\"}";
	}
	
}
