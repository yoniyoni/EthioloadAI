# JWT Configuration Guide
## EthioloadAI Spring Boot Backend

**Document Version:** 1.0  
**Last Updated:** July 9, 2026  
**Applies to:** Spring Boot 3.2.0, JWT 0.12.3

---

## Overview

This document explains how to configure the JWT_SECRET environment variable for the EthioloadAI backend application. The JWT secret is critical for security and must be properly configured before the application can start.

---

## Where JWT_SECRET is Loaded From

The application loads `JWT_SECRET` from environment variables in the following order:

1. **Environment Variable:** `JWT_SECRET` (highest priority)
2. **Spring Boot Property:** `jwt.secret` (from application.yml or profile-specific files)

**Configuration Files:**
- `application.yml` - Base configuration: `jwt.secret: ${JWT_SECRET}`
- `application-dev.yml` - Development profile: `jwt.secret: ${JWT_SECRET}`
- `application-prod.yml` - Production profile: `jwt.secret: ${JWT_SECRET}`
- `application-test.yml` - Test profile: `jwt.secret: ${JWT_SECRET`

**Important:** All profiles now consistently read from the `JWT_SECRET` environment variable. No hardcoded secrets are present in the codebase.

---

## Minimum Required Length

**Minimum:** 32 characters  
**Recommended:** 64+ characters (cryptographically secure)

The application enforces a minimum length of 32 characters for the JWT secret. This validation exists for the following security reasons:

### Why the Validation Exists

1. **HMAC-SHA256 Security:** The application uses HMAC-SHA256 for JWT signing. While the algorithm itself doesn't require a specific key length, shorter keys are vulnerable to brute force attacks.

2. **Prevent Weak Defaults:** Without validation, developers might accidentally use weak secrets like "secret" or "password" during development, which could accidentally be deployed to production.

3. **Industry Best Practices:** OWASP recommends using secrets with at least 256 bits of entropy (32 bytes/characters for ASCII).

4. **Future-Proofing:** Longer secrets provide a security buffer against advances in computing power and cryptanalysis.

### What Happens if Secret is Too Short

If `JWT_SECRET` is less than 32 characters, the application will fail to start with an error message:

```
JWT_SECRET must be at least 32 characters long. Current length: [X]
```

---

## How to Set JWT_SECRET

### Windows (Command Prompt)

```cmd
set JWT_SECRET=your-32-character-or-longer-secret-here
```

### Windows (PowerShell)

```powershell
$env:JWT_SECRET="your-32-character-or-longer-secret-here"
```

### Windows (System Environment Variable)

1. Right-click "This PC" → Properties → Advanced system settings
2. Click "Environment Variables"
3. Under "User variables" or "System variables", click "New"
4. Variable name: `JWT_SECRET`
5. Variable value: your secret
6. Click OK to save

### Linux / macOS (Bash/Zsh)

```bash
export JWT_SECRET="your-32-character-or-longer-secret-here"
```

To make it permanent, add to `~/.bashrc` or `~/.zshrc`:

```bash
echo 'export JWT_SECRET="your-32-character-or-longer-secret-here"' >> ~/.bashrc
source ~/.bashrc
```

### Docker (docker-compose.yml)

```yaml
version: '3.8'
services:
  backend:
    image: ethioloadai-backend:latest
    environment:
      - JWT_SECRET=your-32-character-or-longer-secret-here
      - DATABASE_URL=jdbc:postgresql://db:5432/ethioloadai
      - DATABASE_USERNAME=ethioloadai
      - DATABASE_PASSWORD=password
```

### Docker (Dockerfile)

```dockerfile
ENV JWT_SECRET=your-32-character-or-longer-secret-here
```

### Docker (docker run)

```bash
docker run -e JWT_SECRET="your-32-character-or-longer-secret-here" ethioloadai-backend:latest
```

### Docker (.env file)

Create a `.env` file in the same directory as `docker-compose.yml`:

```env
JWT_SECRET=your-32-character-or-longer-secret-here
DATABASE_URL=jdbc:postgresql://db:5432/ethioloadai
DATABASE_USERNAME=ethioloadai
DATABASE_PASSWORD=password
```

Then reference it in `docker-compose.yml`:

```yaml
version: '3.8'
services:
  backend:
    image: ethioloadai-backend:latest
    env_file:
      - .env
```

### Render (Deployment)

1. Go to your Render dashboard
2. Select your web service
3. Go to "Environment" tab
4. Click "Add Environment Variable"
5. Key: `JWT_SECRET`
6. Value: your secret
7. Click "Save Changes"
8. Render will automatically redeploy with the new environment variable

### Other Cloud Platforms

**AWS Elastic Beanstalk:**
- Add to environment configuration in the console
- Or use `.ebextensions` configuration files

**AWS ECS:**
- Add to task definition environment variables
- Or use AWS Secrets Manager

**Google Cloud Platform:**
- Add to Cloud Run environment variables
- Or use Secret Manager

**Azure App Service:**
- Add to Application Settings in the Azure portal

**Heroku:**
```bash
heroku config:set JWT_SECRET="your-32-character-or-longer-secret-here"
```

---

## Generating a Secure JWT Secret

### Using OpenSSL (Linux/macOS/Windows)

```bash
openssl rand -base64 32
```

### Using Python

```python
import secrets
print(secrets.token_urlsafe(32))
```

### Using Node.js

```javascript
const crypto = require('crypto');
console.log(crypto.randomBytes(32).toString('base64'));
```

### Using PowerShell

```powershell
$bytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($bytes)
$random.Dispose()
[Convert]::ToBase64String($bytes)
```

### Online Generators (Use with Caution)

You can use online random string generators, but:
- Only use reputable sites
- Generate locally when possible
- Never share the generated secret
- Use HTTPS only

---

## Security Best Practices

### DO
- Generate secrets using cryptographically secure random number generators
- Use at least 32 characters (64+ recommended)
- Store secrets in environment variables or secret management systems
- Rotate secrets periodically (every 90 days recommended)
- Use different secrets for development, staging, and production
- Keep secrets out of version control
- Use `.env.example` as a template (not for actual secrets)

### DO NOT
- Hardcode secrets in source code
- Commit secrets to version control (even private repos)
- Use weak secrets like "password", "secret", "12345678"
- Share secrets via email, chat, or unencrypted channels
- Use the same secret across multiple applications
- Include secrets in error messages or logs
- Use predictable patterns like "my-secret-key-12345"

---

## Troubleshooting

### Application Fails to Start

**Error:** `JWT_SECRET must be at least 32 characters long`

**Solution:** Set the `JWT_SECRET` environment variable with a value of at least 32 characters.

### Secret Not Being Read

**Symptoms:** Application uses default or fails to find secret

**Solutions:**
1. Verify environment variable is set: `echo $JWT_SECRET` (Linux/macOS) or `echo %JWT_SECRET%` (Windows)
2. Check for typos in variable name (case-sensitive)
3. Ensure environment variable is set before starting the application
4. Restart your terminal/shell after setting environment variables
5. Check that no other configuration is overriding the environment variable

### Docker Container Issues

**Symptoms:** Container starts but fails with JWT error

**Solutions:**
1. Verify environment variable is passed to container: `docker inspect <container-id>`
2. Check `.env` file is in the correct directory
3. Ensure `env_file` or `environment` is correctly configured in docker-compose.yml
4. Rebuild container after changing environment variables

### Production Deployment Issues

**Symptoms:** Works locally but fails in production

**Solutions:**
1. Verify environment variable is set in your cloud platform
2. Check cloud platform logs for specific error messages
3. Ensure secret is not being masked or truncated by the platform
4. Verify the platform supports environment variables of your secret length

---

## Testing Your Configuration

### Verify Secret is Set

**Linux/macOS:**
```bash
echo $JWT_SECRET | wc -c
```

**Windows (PowerShell):**
```powershell
$env:JWT_SECRET.Length
```

**Windows (Command Prompt):**
```cmd
echo %JWT_SECRET%
```

### Test Application Startup

```bash
# Linux/macOS
export JWT_SECRET="your-32-character-or-longer-secret-here"
mvn spring-boot:run

# Windows (PowerShell)
$env:JWT_SECRET="your-32-character-or-longer-secret-here"
mvn spring-boot:run

# Windows (Command Prompt)
set JWT_SECRET=your-32-character-or-longer-secret-here
mvn spring-boot:run
```

If the application starts successfully, your JWT configuration is correct.

---

## Related Files

- `.env.example` - Template for environment variables
- `application.yml` - Base Spring Boot configuration
- `application-dev.yml` - Development profile configuration
- `application-prod.yml` - Production profile configuration
- `application-test.yml` - Test profile configuration

---

## Additional Resources

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
- [JWT.io Introduction](https://jwt.io/introduction)

---

## Support

If you encounter issues not covered in this document, please:
1. Check the application logs for specific error messages
2. Verify your environment variables are set correctly
3. Ensure you're using the correct profile (dev/prod/test)
4. Review the Spring Boot documentation for your specific deployment platform

---

**Document Status:** COMPLETE  
**Next Review Date:** As needed when JWT configuration changes
