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

import io.getlime.powerauth.soap.ActivationStatus;
import io.getlime.powerauth.soap.GetActivationStatusResponse;
import io.getlime.powerauth.soap.GetPersonalizedEncryptionKeyResponse;
import io.getlime.push.model.CreateDeviceRegistrationRequest;
import io.getlime.push.model.RemoveDeviceRegistrationRequest;
import io.getlime.push.model.StatusResponse;
import io.getlime.push.model.UpdateStatusRequest;
import io.getlime.push.repository.DeviceRegistrationRepository;
import io.getlime.push.repository.model.DeviceRegistration;
import io.getlime.security.powerauth.soap.spring.client.PowerAuthServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.List;

/**
 * Controller responsible for device registration related business processes.
 *
 * @author Petr Dvorak, petr@lime-company.eu
 */
@Controller
@RequestMapping(value = "push/device")
public class DeviceManagementController {
    
    private DeviceRegistrationRepository deviceRegistrationRepository;
    private PowerAuthServiceClient client;

    @Autowired
    public DeviceManagementController(DeviceRegistrationRepository deviceRegistrationRepository) {
        this.deviceRegistrationRepository = deviceRegistrationRepository;
    }

    @Autowired
    void setClient(PowerAuthServiceClient client) {
        this.client = client;
    }

    /**
     * Create a new device registration.
     * @param request Device registration request.
     * @return Device registration status.
     */
    @RequestMapping(value = "create", method = RequestMethod.POST)
    @Transactional
    public @ResponseBody StatusResponse createDevice(@RequestBody CreateDeviceRegistrationRequest request) {

        Long appId = request.getAppId();
        String pushToken = request.getToken();
        String platform = request.getPlatform();
        String activationId = request.getActivationId();

        DeviceRegistration registration = deviceRegistrationRepository.findFirstByAppIdAndPushToken(appId, pushToken);
        if (registration == null) {
            registration = new DeviceRegistration();
            registration.setAppId(appId);
            registration.setPushToken(pushToken);
        }
        registration.setLastRegistered(new Date());
        registration.setPlatform(platform);

        if (activationId != null) {
            final GetActivationStatusResponse activation = client.getActivationStatus(activationId);
            if (activation != null && ActivationStatus.REMOVED.equals(activation.getActivationStatus())) {
                registration.setActivationId(activationId);
                registration.setActive(activation.getActivationStatus().equals(ActivationStatus.ACTIVE));
                registration.setUserId(activation.getUserId());
                if (activation.getActivationStatus().equals(ActivationStatus.ACTIVE)) {
                    final GetPersonalizedEncryptionKeyResponse encryptionKeyResponse = client.generatePersonalizedE2EEncryptionKey(activationId, null);
                    if (encryptionKeyResponse != null) {
                        registration.setEncryptionKey(encryptionKeyResponse.getEncryptionKey());
                        registration.setEncryptionKeyIndex(encryptionKeyResponse.getEncryptionKeyIndex());
                    }
                }
            }
        }

        deviceRegistrationRepository.save(registration);

        StatusResponse response = new StatusResponse();
        response.setStatus(StatusResponse.OK);
        return response;
    }

    /**
     * Update status for given device registration.
     * @param request Status update request.
     * @return Status update response.
     */
    @RequestMapping(value = "status/update", method = RequestMethod.POST)
    @Transactional
    public @ResponseBody  StatusResponse updateActivationStatus(@RequestBody UpdateStatusRequest request) {

        String activationId = request.getActivationId();

        List<DeviceRegistration> registrations = deviceRegistrationRepository.findByActivationId(activationId);
        if (registrations != null)  {
            ActivationStatus status = client.getActivationStatus(activationId).getActivationStatus();
            for (DeviceRegistration registration: registrations) {
                registration.setActive(status.equals(ActivationStatus.ACTIVE));
                deviceRegistrationRepository.save(registration);
            }
        }

        StatusResponse response = new StatusResponse();
        response.setStatus(StatusResponse.OK);
        return response;
    }

    /**
     * Remove device registration with given push token.
     * @param request Remove registration request.
     * @return Removal status response.
     */
    @RequestMapping(value = "remove", method = RequestMethod.POST)
    @Transactional
    public @ResponseBody StatusResponse deleteActivationStatus(@RequestBody RemoveDeviceRegistrationRequest request) {

        Long appId = request.getAppId();
        String pushToken = request.getToken();

        DeviceRegistration registration = deviceRegistrationRepository.findFirstByAppIdAndPushToken(appId, pushToken);
        if (registration != null)  {
            deviceRegistrationRepository.delete(registration);
        }

        StatusResponse response = new StatusResponse();
        response.setStatus(StatusResponse.OK);
        return response;
    }

}
