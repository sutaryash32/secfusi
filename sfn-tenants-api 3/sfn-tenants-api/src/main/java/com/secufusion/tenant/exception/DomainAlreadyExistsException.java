package com.secufusion.tenant.exception;

import com.secufusion.tenant.util.ResponseCodes;

public class DomainAlreadyExistsException extends GlobalException
{
    public DomainAlreadyExistsException(String message)
    {
        super(message, ResponseCodes.DOMAIN_ALREADY_EXISTS);
    }

}
