/*
 *  Tanaguru - Automated webpage assessment
 *  Copyright (C) 2008-2011  Open-S Company
 * 
 *  This file is part of Tanaguru.
 * 
 *  Tanaguru is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 * 
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Contact us by mail: open-s AT open-s DOT com
 */

package org.opens.tanaguru.service.command;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.log4j.Logger;
import org.opens.tanaguru.contentadapter.AdaptationListener;
import org.opens.tanaguru.entity.audit.*;
import org.opens.tanaguru.entity.parameterization.Parameter;
import org.opens.tanaguru.entity.reference.Test;
import org.opens.tanaguru.entity.service.audit.AuditDataService;
import org.opens.tanaguru.entity.service.audit.ContentDataService;
import org.opens.tanaguru.entity.service.audit.ProcessResultDataService;
import org.opens.tanaguru.entity.service.parameterization.ParameterDataService;
import org.opens.tanaguru.entity.service.reference.TestDataService;
import org.opens.tanaguru.entity.service.subject.WebResourceDataService;
import org.opens.tanaguru.entity.subject.Page;
import org.opens.tanaguru.entity.subject.Site;
import org.opens.tanaguru.entity.subject.WebResource;
import org.opens.tanaguru.service.*;
import org.opens.tanaguru.util.MD5Encoder;

/**
 *
 * @author jkowalczyk
 */
public abstract class AuditCommandImpl implements AuditCommand {

    private static final Logger LOGGER = Logger.getLogger(AuditCommandImpl.class);
    
    public static final String AUDIT_STATUS_IS_LOGGER_STR = "Audit status is";
    public static final String WHILE_LOGGER_STR = " while";
    public static final String WAS_REQUIRED_LOGGER_STR = " was required";
    public static final String TO_LOGGER_STR = " to ";
    public static final String MS_LOGGER_STR = " ms ";
    public static final String SSP_TOOK_LOGGER_STR = " SSP took ";
    public static final String CONSOLIDATING_TOOK_LOGGER_STR = "Consolidating took ";
    
    public static final int ANALYSE_TREATMENT_WINDOW = 50;
    public static final int PROCESSING_TREATMENT_WINDOW = 25;
    public static final int ADAPTATION_TREATMENT_WINDOW = 25;
    public static final int CONSOLIDATION_TREATMENT_WINDOW = 1000;
    
    private Audit audit;
    @Override
    public Audit getAudit() {
        return audit;
    }
    
    @Override
    public void setAudit(Audit audit) {
        this.audit = audit;
    }
    
    // The dataServices
    
    private AuditDataService auditDataService;
    public AuditDataService getAuditDataService() {
        return auditDataService;
    }

    private TestDataService testDataService;    
    public TestDataService getTestDataService() {
        return testDataService;
    }
    
    private ParameterDataService parameterDataService;
    public ParameterDataService getParameterDataService() {
        return parameterDataService;
    }
    
    private WebResourceDataService webResourceDataService;
    public WebResourceDataService getWebResourceDataService() {
        return webResourceDataService;
    }
    
    private ContentDataService contentDataService;
    public ContentDataService getContentDataService() {
        return contentDataService;
    }
    
    private ProcessResultDataService processResultDataService;
    public ProcessResultDataService getProcessResultDataService() {
        return processResultDataService;
    }
    
    // The services
    
    private ContentAdapterService contentAdapterService;
    public ContentAdapterService getContentAdapterService() {
        return contentAdapterService;
    }

    private ProcessorService processorService;
    public ProcessorService getProcessorService() {
        return processorService;
    }
    
    private ConsolidatorService consolidatorService;
    public ConsolidatorService getConsolidatorService() {
        return consolidatorService;
    }
    
    private AnalyserService analyserService;
    public AnalyserService getAnalyserService() {
        return analyserService;
    }

    // The listeners
    
    private AdaptationListener adaptationListener;
    public AdaptationListener getAdaptationListener() {
        return adaptationListener;
    }
    
    /**
     * 
     * @param paramSet
     * @param auditDataService
     * @param testDataService
     * @param parameterDataService
     * @param webResourceDataService
     * @param contentDataService
     * @param processResultDataService
     * @param contentAdapterService
     * @param processorService
     * @param consolidatorService
     * @param analyserService
     * @param adaptationListener 
     */
    public AuditCommandImpl(
            Set<Parameter> paramSet,
            AuditDataService auditDataService, 
            TestDataService testDataService, 
            ParameterDataService parameterDataService,
            WebResourceDataService webResourceDataService,
            ContentDataService contentDataService, 
            ProcessResultDataService processResultDataService, 
            ContentAdapterService contentAdapterService, 
            ProcessorService processorService, 
            ConsolidatorService consolidatorService, 
            AnalyserService analyserService, 
            AdaptationListener adaptationListener) {
        this.auditDataService = auditDataService;
        this.testDataService = testDataService;
        this.parameterDataService = parameterDataService;
        this.webResourceDataService = webResourceDataService;
        this.contentDataService = contentDataService;
        this.processResultDataService = processResultDataService;
        this.contentAdapterService = contentAdapterService;
        this.processorService = processorService;
        this.consolidatorService = consolidatorService;
        this.analyserService = analyserService;
        this.adaptationListener = adaptationListener;
        initialiseAudit(paramSet);
    }
    
    /**
     * 
     * @param paramSet
     * @return 
     */
    private Audit initialiseAudit (Set<Parameter> paramSet) {
        Logger.getLogger(this.getClass()).info("initializeAudit");
        // the paramSet has to be persisted
        parameterDataService.saveOrUpdate(paramSet);
        audit = auditDataService.create();
        audit.setTestList(testDataService.getTestListFromParamSet(paramSet));
        audit.setParameterSet(paramSet);
        setStatusToAudit(AuditStatus.INITIALISATION);
        return audit;
    }
    
    @Override
    public void adaptContent() {
        audit = auditDataService.getAuditWithWebResource(audit.getId());
        if (!audit.getStatus().equals(AuditStatus.CONTENT_ADAPTING)) {
            LOGGER.warn(
                    new StringBuilder(AUDIT_STATUS_IS_LOGGER_STR)
                    .append(audit.getStatus())
                    .append(WHILE_LOGGER_STR)
                    .append(AuditStatus.CONTENT_ADAPTING)
                    .append(WAS_REQUIRED_LOGGER_STR).toString());
            return;
        }

        // debug tools
        Date beginProcessDate = null;
        Date endRetrieveDate = null;
        Date endProcessDate = null;
        Date endPersistDate;
        Long persistenceDuration = Long.valueOf(0);

        boolean hasCorrectDOM = false;
        Long i = Long.valueOf(0);
        Long webResourceId = audit.getSubject().getId();
        Long nbOfContent = contentDataService.getNumberOfSSPFromWebResource(audit.getSubject(), HttpStatus.SC_OK);

        // Some actions have to be realized when the adaptation starts
        if (adaptationListener != null) {
            adaptationListener.adaptationStarted(audit);
        }
        while (i.compareTo(nbOfContent) < 0) {
            if (LOGGER.isDebugEnabled()) {
                beginProcessDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Adapt ssp from  ")
                            .append(i)
                            .append(TO_LOGGER_STR)
                            .append(i + ADAPTATION_TREATMENT_WINDOW).toString());
            }
            List<Content> contentList = retrieveContentList(
                                            webResourceId, 
                                            i, 
                                            ADAPTATION_TREATMENT_WINDOW, 
                                            beginProcessDate, 
                                            false);
            if (LOGGER.isDebugEnabled()) {
                endRetrieveDate = Calendar.getInstance().getTime();
            }
            Set<Content> contentSet = new HashSet<Content>();
            contentSet.addAll(contentAdapterService.adaptContent(contentList));
            if (LOGGER.isDebugEnabled()) {
                endProcessDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Adapting  ")
                            .append(contentList.size())
                            .append(SSP_TOOK_LOGGER_STR)
                            .append(endProcessDate.getTime() - endRetrieveDate.getTime())
                            .append(MS_LOGGER_STR)
                            .append(contentSet.size()).toString());
            }

            hasCorrectDOM = hasCorrectDOM || hasContentSetAtLeastOneCorrectDOM(contentSet);
            
            this.encodeSourceAndPersistContentList(contentSet);

            if (LOGGER.isDebugEnabled()) {
                endPersistDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Persisting  ") 
                            .append(contentSet.size())
                            .append(SSP_TOOK_LOGGER_STR) 
                            .append(endPersistDate.getTime() - endProcessDate.getTime())
                            .append(MS_LOGGER_STR).toString());
                persistenceDuration = persistenceDuration
                        + (endPersistDate.getTime() - endProcessDate.getTime());
            }
            i = i + ADAPTATION_TREATMENT_WINDOW;
            // explicit call of the Gc
            System.gc();
        }
        
        LOGGER.debug(new StringBuilder("Application spent ")
                        .append(persistenceDuration)
                        .append(" ms to write in Disk while adapting").toString());
        
        if (hasCorrectDOM) {
            setStatusToAudit(AuditStatus.PROCESSING);
        } else {
            Logger.getLogger(AuditServiceImpl.class).warn("Audit has no corrected DOM");
            setStatusToAudit(AuditStatus.ERROR);
        }
        
        // Some actions have to be realized when the adaptation is completed
        if (adaptationListener != null) {
            adaptationListener.adaptationCompleted(audit);
        }
    }

    /**
     * This method retrieves a list of content for a given webResource 
     * from a startValue regarding the ADAPTATION_TREATMENT_WINDOW
     * 
     * @param webResourceId
     * @param startValue
     * @return 
     */
    private List<Content> retrieveContentList(
            Long webResourceId, 
            Long startValue, 
            int windowSize,
            Date beginProcessDate, 
            boolean getContentWithRelatedContent) {

        List<Content> contentList = new ArrayList<Content>();
        
        // First we retrieve a list of Ids
        List<Long> contentIdList = contentDataService.getSSPFromWebResource(
                                webResourceId,
                                HttpStatus.SC_OK,
                                startValue.intValue(),
                                windowSize);
        
        LOGGER.debug(new StringBuilder("Retrieved ")
                        .append(contentIdList.size())
                        .append(" Ids ").toString());
        
        // we retrieve each content from its ID and add it to the contentList 
        // that will be returned
        for (Long id : contentIdList) {
            Content content;
            if (getContentWithRelatedContent) {
                content = contentDataService.readWithRelatedContent(id, true);
            } else {
                content = contentDataService.read(id);
            }
            if (content != null) {
                contentList.add(content);
            }
        }
        
        if (LOGGER.isDebugEnabled()) {
            long length = 0;
            int nbOfResources = 0;
            for (Content content : contentList) {
                if (((SSP) content).getSource() != null) {
                    length += ((SSP) content).getSource().length();
                    if (getContentWithRelatedContent) {
                        nbOfResources += ((SSP) content).getRelatedContentSet().size();
                    }
                }
            }
            StringBuilder debugMessage = new StringBuilder("Retrieving  ")
                        .append(contentList.size())
                        .append(SSP_TOOK_LOGGER_STR)
                        .append(Calendar.getInstance().getTime().getTime() - beginProcessDate.getTime())
                        .append(" ms and working on ")
                        .append(length)
                        .append(" characters");
            if (getContentWithRelatedContent) {
                debugMessage.append(" and ");
                debugMessage.append(nbOfResources);
                debugMessage.append(" relatedContent ");
            }
            LOGGER.debug(debugMessage.toString());
        }
        return contentList;
    }
    
    /**
     * 
     * @param contentSet
     * @return 
     */
    private boolean hasContentSetAtLeastOneCorrectDOM(Set<Content> contentSet) {
        for (Content content : contentSet) {
            // if one SSP with not empty DOM is encountered, we return true
            if (content instanceof SSP && 
                !((SSP) content).getDOM().isEmpty()) {
                
                return true;
            }
        }
        return false;
    }
    
    /**
     * Encode Source code and persist the content list
     * 
     * @param contentSet 
     */
    private void encodeSourceAndPersistContentList(Set<Content> contentSet) {
        for (Content content : contentSet) {
            if (content instanceof SSP && 
                !((SSP) content).getDOM().isEmpty()) {
                try {
                    ((SSP) content).setSource(MD5Encoder.MD5(((SSP) content).getSource()));
                } catch (NoSuchAlgorithmException ex) {
                    LOGGER.warn(ex);
                } catch (UnsupportedEncodingException ex) {
                    LOGGER.warn(ex);
                }
            }
            contentDataService.saveOrUpdate(content);
        }
    }
    
    @Override
    public void process() {
        audit = auditDataService.getAuditWithTest(audit.getId());
        if (!audit.getStatus().equals(AuditStatus.PROCESSING)) {
            LOGGER.warn(
                    new StringBuilder(AUDIT_STATUS_IS_LOGGER_STR)
                    .append(audit.getStatus())
                    .append(WHILE_LOGGER_STR)
                    .append(AuditStatus.PROCESSING)
                    .append(WAS_REQUIRED_LOGGER_STR).toString());
            return;
        }

        // debug tools
        Date beginProcessDate = null;
        Date endProcessDate = null;
        Date endPersistDate;
        Long persistenceDuration = Long.valueOf(0);

        Long i = Long.valueOf(0);
        Long webResourceId = audit.getSubject().getId();
        Long nbOfContent = contentDataService.getNumberOfSSPFromWebResource(audit.getSubject(), HttpStatus.SC_OK);

        Set<ProcessResult> processResultSet = new HashSet<ProcessResult>();
        
        while (i.compareTo(nbOfContent) < 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        new StringBuilder("Processing from ")
                            .append(i)
                            .append(TO_LOGGER_STR)
                            .append(i)
                            .append(PROCESSING_TREATMENT_WINDOW).toString());
                beginProcessDate = Calendar.getInstance().getTime();
            }
            List<Content> contentList = retrieveContentList(
                                            webResourceId, 
                                            i, 
                                            PROCESSING_TREATMENT_WINDOW, 
                                            beginProcessDate, 
                                            true);
            processResultSet.clear();
            processResultSet.addAll(processorService.process(contentList, (List<Test>) audit.getTestList()));
            for (ProcessResult processResult : processResultSet) {
                processResult.setGrossResultAudit(audit);
            }

            if (LOGGER.isDebugEnabled()) {
                endProcessDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Processing of ")
                            .append(PROCESSING_TREATMENT_WINDOW)
                            .append(" elements took ")
                            .append(endProcessDate.getTime() - beginProcessDate.getTime())
                            .append(MS_LOGGER_STR).toString());
            }
            processResultDataService.saveOrUpdate(processResultSet);
            if (LOGGER.isDebugEnabled()) {
                endPersistDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Persist processing of ")
                            .append(PROCESSING_TREATMENT_WINDOW)
                            .append(" elements took ")
                            .append(endPersistDate.getTime() - endProcessDate.getTime())
                            .append(MS_LOGGER_STR).toString());
                persistenceDuration = persistenceDuration
                        + (endPersistDate.getTime() - endProcessDate.getTime());
            }
            i = i + PROCESSING_TREATMENT_WINDOW;
            System.gc();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    new StringBuilder("Application spent ")
                        .append(persistenceDuration)
                        .append(" ms to write in Disk while processing").toString());
        }

        if (processResultDataService.getNumberOfGrossResultFromAudit(audit) > 0) {
            setStatusToAudit(AuditStatus.CONSOLIDATION);
        } else {
            LOGGER.error("Audit has no gross result");
            setStatusToAudit(AuditStatus.ERROR);
        }
    }
    
    @Override
    public void consolidate() {
        audit = auditDataService.getAuditWithTest(audit.getId());
        if (!audit.getStatus().equals(AuditStatus.CONSOLIDATION)) {
            LOGGER.warn(
                    new StringBuilder(AUDIT_STATUS_IS_LOGGER_STR)
                    .append(audit.getStatus())
                    .append(WHILE_LOGGER_STR)
                    .append(AuditStatus.CONSOLIDATION)
                    .append(WAS_REQUIRED_LOGGER_STR).toString());
            return;
        }

        // debug tools
        Date beginProcessDate = null;
        Date endProcessDate = null;
        Date endPersistDate = null;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Consolidation");
            beginProcessDate = Calendar.getInstance().getTime();
        }
        if (audit.getSubject() instanceof Page) {
            consolidate((List<ProcessResult>) processResultDataService.
                    getGrossResultFromAudit(audit), (List<Test>)audit.getTestList());
            if (LOGGER.isDebugEnabled()) {
                endProcessDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder(CONSOLIDATING_TOOK_LOGGER_STR)
                            .append(endProcessDate.getTime()-beginProcessDate.getTime())
                            .append(MS_LOGGER_STR).toString());
            }
        } else if (audit.getSubject() instanceof Site) {
            if (contentDataService.getNumberOfSSPFromWebResource(audit.getSubject(), HttpStatus.SC_OK) > 20) {
                List<Test> testList = new ArrayList<Test>();
                for (Test test : audit.getTestList()) {
                    testList.add(test);

                    List<ProcessResult> prList= (List<ProcessResult>) processResultDataService.
                            getGrossResultFromAuditAndTest(audit, test);
                    consolidate(prList, testList);
                    testList.clear();
                }
                if (LOGGER.isDebugEnabled()) {
                    endProcessDate = Calendar.getInstance().getTime();
                    LOGGER.debug(
                            new StringBuilder(CONSOLIDATING_TOOK_LOGGER_STR)
                                .append(endProcessDate.getTime()-beginProcessDate.getTime())
                                .append(MS_LOGGER_STR).toString());
                }
            } else {
                List<ProcessResult> prList= (List<ProcessResult>) processResultDataService.
                            getGrossResultFromAudit(audit);
                consolidate(prList, (List<Test>)audit.getTestList());
                if (LOGGER.isDebugEnabled()) {
                    endProcessDate = Calendar.getInstance().getTime();
                    LOGGER.debug(
                            new StringBuilder(CONSOLIDATING_TOOK_LOGGER_STR)
                                .append(endProcessDate.getTime()-beginProcessDate.getTime())
                                .append(MS_LOGGER_STR).toString());
                }
            }
        }
        audit = auditDataService.saveOrUpdate(audit);
        if (LOGGER.isDebugEnabled()) {
            endPersistDate = Calendar.getInstance().getTime();
            LOGGER.debug(
                    new StringBuilder("Persisting Consolidation of the audit took")
                        .append(endPersistDate.getTime() - endProcessDate.getTime())
                        .append(MS_LOGGER_STR).toString());
        }
    }

    /**
     * 
     * @param prList
     * @param testList 
     */
    private void consolidate(List<ProcessResult> prList, List<Test> testList) {
        Set<ProcessResult> processResultSet = new HashSet<ProcessResult>();
        if (LOGGER.isDebugEnabled()) {
            if (testList.size() == 1) {
                LOGGER.debug(
                        new StringBuilder("Consolidate ")
                            .append(prList.size())
                            .append(" elements for test ")
                            .append(testList.iterator().next().getCode()).toString());
            } else {
                LOGGER.debug(
                        new StringBuilder("Consolidate ")
                            .append(prList.size())
                            .append(" elements for ")
                            .append(testList.size())
                            .append(" tests ").toString());
            }
        }
        processResultSet.addAll(consolidatorService.consolidate(
                prList,
                testList));
        // To avoid errors with processResult of Site Type in case of page audit
        Set<ProcessResult> resultToRemoveSet = new HashSet<ProcessResult>();
        for (ProcessResult processResult : processResultSet) {
            if (processResult.getTest().getScope().getCode().equalsIgnoreCase("site")
                    && processResult.getSubject() instanceof Page) {
                resultToRemoveSet.add(processResult);
            } else {
                processResult.setNetResultAudit(audit);
            }
        }
        for (ProcessResult resultToRemove : resultToRemoveSet) {
            processResultSet.remove(resultToRemove);
        }
        if (!processResultSet.isEmpty()) {
            audit.setStatus(AuditStatus.ANALYSIS);
        } else {
            LOGGER.warn("Audit has no net result");
            audit.setStatus(AuditStatus.ERROR);
        }
        Iterator<ProcessResult> iter = processResultSet.iterator();
        Set<ProcessResult> processResultSubset = new HashSet<ProcessResult>();
        int i = 0;
        while (iter.hasNext()) {
            processResultSubset.add(iter.next());
            i++;
            if (i % CONSOLIDATION_TREATMENT_WINDOW == 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            new StringBuilder("Persisting Consolidation from ")
                                .append(i)
                                .append(TO_LOGGER_STR)
                                .append(i+CONSOLIDATION_TREATMENT_WINDOW).toString());
                }
                processResultDataService.saveOrUpdate(processResultSubset);
                processResultSubset.clear();
            }
        }
        processResultDataService.saveOrUpdate(processResultSubset);
        processResultSubset.clear();
        System.gc();
    }

    @Override
    public void analyse() {
        audit = auditDataService.getAuditWithWebResource(audit.getId());
        if (!audit.getStatus().equals(AuditStatus.ANALYSIS)) {
            LOGGER.warn(
                    new StringBuilder(AUDIT_STATUS_IS_LOGGER_STR)
                    .append(audit.getStatus())
                    .append(WHILE_LOGGER_STR)
                    .append(AuditStatus.ANALYSIS)
                    .append(WAS_REQUIRED_LOGGER_STR).toString());
            return ;
        }
        // debug tools
        Date beginProcessDate = null;
        Date endProcessDate = null;
        Date endPersistDate = null;
        Long persistenceDuration = Long.valueOf(0);
        //

        WebResource parentWebResource = audit.getSubject();
        if (parentWebResource instanceof Page) {
            analyserService.analyse(parentWebResource);
            webResourceDataService.saveOrUpdate(parentWebResource);
        } else if (parentWebResource instanceof Site) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Analysing results of scope site");
                beginProcessDate = Calendar.getInstance().getTime();
            }
            analyserService.analyse(parentWebResource);
            if (LOGGER.isDebugEnabled()) {
                endProcessDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Analysing results of scope site took ")
                        .append(endProcessDate.getTime() - beginProcessDate.getTime())
                        .append(MS_LOGGER_STR).toString());
            }
            webResourceDataService.saveOrUpdate(parentWebResource);

            if (LOGGER.isDebugEnabled()) {
                endPersistDate = Calendar.getInstance().getTime();
                LOGGER.debug(
                        new StringBuilder("Persisting Analysis results of scope site ")
                            .append(endPersistDate.getTime() - endProcessDate.getTime())
                            .append(MS_LOGGER_STR).toString());
                persistenceDuration = persistenceDuration
                        + (endPersistDate.getTime() - endProcessDate.getTime());
            }

            Long nbOfContent =
                    webResourceDataService.getNumberOfChildWebResource(parentWebResource);
            Long i = Long.valueOf(0);
            List<WebResource> webResourceList = null;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        new StringBuilder("Analysing ")
                            .append(nbOfContent)
                            .append(" elements ").toString());
            }
            while (i.compareTo(nbOfContent) < 0) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            new StringBuilder("Analysing results of scope page from ")
                                .append(i)
                                .append(TO_LOGGER_STR)
                                .append(i + ANALYSE_TREATMENT_WINDOW).toString());
                    beginProcessDate = Calendar.getInstance().getTime();
                }
                webResourceList = webResourceDataService.getWebResourceFromItsParent(
                        parentWebResource,
                        i.intValue(),
                        ANALYSE_TREATMENT_WINDOW);
                for (WebResource webResource : webResourceList) {
                    if (LOGGER.isDebugEnabled()) {
                        endProcessDate = Calendar.getInstance().getTime();
                        LOGGER.debug(
                                new StringBuilder("Analysing results for page ")
                                    .append(webResource.getURL())
                                    .append(" took ")
                                    .append(endProcessDate.getTime() - beginProcessDate.getTime())
                                    .append(MS_LOGGER_STR).toString());
                    }
                    analyserService.analyse(webResource);
                    if (LOGGER.isDebugEnabled()) {
                        endPersistDate = Calendar.getInstance().getTime();
                        LOGGER.debug(
                                new StringBuilder("Persisting Analysis results for page ")
                                    .append(webResource.getURL())
                                    .append(" took ")
                                    .append(endPersistDate.getTime() - endProcessDate.getTime())
                                    .append(MS_LOGGER_STR).toString());
                        persistenceDuration = persistenceDuration
                                + (endPersistDate.getTime() - endProcessDate.getTime());
                    }
                }
                i = i + ANALYSE_TREATMENT_WINDOW;
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    new StringBuilder("Application spent ")
                    .append(persistenceDuration)
                    .append(" ms to write in Disk while analysing").toString());
        }

        setStatusToAudit(AuditStatus.COMPLETED);
    }
    
    /**
     * Set a new status to the audit instance and persist it
     * @param auditStatus 
     */
    public void setStatusToAudit(AuditStatus auditStatus) {
        audit.setStatus(auditStatus);
        audit = auditDataService.saveOrUpdate(audit);
    }
    
}