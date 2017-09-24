/*
 * Copyright 2016 Lime - HighTech Solutions s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getlime.push.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.getlime.core.rest.model.base.response.Response;
import io.getlime.push.errorhandling.exceptions.PushServerException;
import io.getlime.push.model.entity.PushMessageBody;
import io.getlime.push.repository.PushCampaignRepository;
import io.getlime.push.repository.model.PushCampaignEntity;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Controller
@RequestMapping(value = "push/campaign/send")
public class SendCampaignController {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job job;

    @Autowired
    private PushCampaignRepository pushCampaignRepository;

    @RequestMapping(value = "{id}", method = RequestMethod.POST)
    @ResponseBody
    public Response sendCampaign(@PathVariable(value = "id") Long id) throws PushServerException {
        try {
            PushCampaignEntity campaign = pushCampaignRepository.findOne(id);
            PushMessageBody pushMessageBody = deserializePushMessageBody(campaign.getMessage());
            if (campaign == null) {
                throw new PushServerException("Campaign with entered id does not exist");
            }

            Map<String, JobParameter> jobParameterMap = new HashMap<>();
            jobParameterMap.put("id", new JobParameter(id));
            jobParameterMap.put("timestamp", new JobParameter(new Date()));
            jobLauncher.run(job, new JobParameters(jobParameterMap));
        } catch (JobExecutionAlreadyRunningException e) {
            throw new PushServerException("Job execution already running");
        } catch (JobRestartException e) {
            throw new PushServerException("Job is restarted");
        } catch (JobInstanceAlreadyCompleteException e) {
            throw new PushServerException("Job instance already completed");
        } catch (JobParametersInvalidException e) {
            throw new PushServerException("Job parameters are invalid");
        }

        return null;
    }


    /**
     * Parsing message from Json to PushMessagebody object
     *
     * @param message message to parse
     * @return PushMessageBody
     */
    private PushMessageBody deserializePushMessageBody(String message) {
        PushMessageBody pushMessageBody = null;
        try {
            pushMessageBody = new ObjectMapper().readValue(message, PushMessageBody.class);
        } catch (IOException e) {
            Logger.getLogger(PushCampaignController.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
        return pushMessageBody;
    }

}
