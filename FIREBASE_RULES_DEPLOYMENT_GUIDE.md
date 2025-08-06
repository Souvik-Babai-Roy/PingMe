# Firebase Security Rules Deployment Guide for PingMe

This guide covers the deployment and configuration of Firebase security rules for the PingMe chat application.

## 📁 Files Created

1. **`database.rules.json`** - Realtime Database security rules
2. **`firestore.rules`** - Firestore security rules  
3. **`storage.rules`** - Firebase Storage security rules

## 🚀 Deployment Instructions

### 1. Firebase CLI Setup

```bash
# Install Firebase CLI (if not already installed)
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize your project (if not already done)
firebase init
```

### 2. Deploy Rules

#### Deploy All Rules at Once:
```bash
firebase deploy --only firestore:rules,database:rules,storage:rules
```

#### Deploy Individual Rule Sets:
```bash
# Deploy Firestore rules only
firebase deploy --only firestore:rules

# Deploy Realtime Database rules only  
firebase deploy --only database:rules

# Deploy Storage rules only
firebase deploy --only storage:rules
```

### 3. Verify Deployment

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Check each service:
   - **Firestore Database** → Rules tab
   - **Realtime Database** → Rules tab  
   - **Storage** → Rules tab

## 🛡️ Security Overview

### Realtime Database Rules (`database.rules.json`)

**Covers:**
- ✅ Chat messages and metadata
- ✅ User chat lists and typing indicators
- ✅ User presence (online/offline status)
- ✅ Blocked users management
- ✅ User settings and preferences
- ✅ Call logs and status updates
- ✅ Data validation and structure enforcement

**Key Security Features:**
- Only chat participants can access messages
- Users can only modify their own data
- Comprehensive data validation
- Protection against malicious data injection
- Automatic blocking and privacy enforcement

### Firestore Rules (`firestore.rules`)

**Covers:**
- ✅ User profiles and authentication
- ✅ Friend relationships and blocking
- ✅ User settings and preferences
- ✅ Status updates and presence
- ✅ Call logs and metadata
- ✅ Reports and feedback system

**Key Security Features:**
- Privacy-aware profile access
- Friend-based content visibility
- Comprehensive user data validation
- Protection against unauthorized access
- Built-in blocking and privacy controls

### Storage Rules (`storage.rules`)

**Covers:**
- ✅ Profile pictures and avatars
- ✅ Chat media (images, videos, audio, documents)
- ✅ Status media and temporary files
- ✅ File type and size validation
- ✅ User-specific folder access

**Key Security Features:**
- File type validation (images, videos, audio, documents)
- Size limits per file type
- User-specific folder access
- Automatic file expiration for temporary content
- Protection against unauthorized uploads

## 🔐 Security Features Explained

### 1. Authentication Requirements
- All rules require user authentication
- Anonymous access is blocked across all services
- Invalid tokens are automatically rejected

### 2. Privacy Controls
- Profile photos respect privacy settings
- Last seen status follows user preferences
- About information visibility control
- Read receipts can be disabled

### 3. Friend-Based Access
- Content visibility based on friend relationships
- Automatic blocking enforcement
- Friend request validation
- Mutual friend verification

### 4. Data Validation
- **Messages**: Content length, type validation, timestamp verification
- **User Data**: Email format, name length, required fields
- **Files**: Type validation, size limits, expiration times
- **Settings**: Boolean validation, enum constraints

### 5. Resource Protection
- **Rate Limiting**: Implicit through Firebase quotas
- **Size Limits**: Enforced per file type and context
- **Time Validation**: Prevents future timestamps
- **Structure Validation**: Ensures data integrity

## 📊 Data Structure Requirements

### User Document (Firestore)
```json
{
  "name": "string (required, 1-50 chars)",
  "email": "string (required, valid email)",
  "phoneNumber": "string (optional)",
  "imageUrl": "string (optional)",
  "about": "string (optional)",
  "profilePhotoEnabled": "boolean (default: true)",
  "lastSeenEnabled": "boolean (default: true)",
  "aboutEnabled": "boolean (default: true)",
  "readReceiptsEnabled": "boolean (default: true)",
  "joinedAt": "timestamp",
  "fcmToken": "string (optional)"
}
```

### Chat Message (Realtime Database)
```json
{
  "senderId": "string (required, must match auth.uid)",
  "content": "string (required, max 5000 chars)",
  "timestamp": "number (required, <= now)",
  "type": "enum (text|image|video|audio|document)",
  "status": "number (1-3, sent|delivered|read)",
  "imageUrl": "string (optional)",
  "fileName": "string (optional)",
  "fileSize": "number (optional)",
  "duration": "number (optional)"
}
```

### Chat Metadata (Realtime Database)
```json
{
  "participants": {
    "userId1": true,
    "userId2": true
  },
  "lastMessage": "string (max 1000 chars)",
  "lastMessageTimestamp": "number",
  "lastMessageSenderId": "string",
  "lastMessageType": "enum",
  "createdAt": "number (required)",
  "isActive": "boolean"
}
```

## 🚨 Security Considerations

### 1. Client-Side Validation
- Rules enforce server-side validation
- Client-side validation improves UX
- Never rely solely on client validation

### 2. Privacy Settings
- Privacy rules are enforced at database level
- App UI should respect these settings
- Users can change privacy at any time

### 3. File Upload Security
- File types are strictly validated
- Size limits prevent abuse
- Malicious files are blocked

### 4. Rate Limiting
- Firebase provides built-in rate limiting
- Consider implementing additional app-level limits
- Monitor usage patterns for abuse

## 🔧 Testing Your Rules

### 1. Firebase Rules Playground
1. Go to Firebase Console → Rules tab
2. Use the "Rules Playground" feature
3. Test different scenarios and user permissions

### 2. Manual Testing
```javascript
// Test authentication requirement
// Try accessing data without auth (should fail)

// Test user data access
// Try accessing another user's data (should fail)

// Test chat access
// Try accessing chat you're not part of (should fail)

// Test file upload
// Try uploading invalid file types (should fail)
```

### 3. Unit Testing (Optional)
```bash
# Install Firebase testing tools
npm install --save-dev @firebase/rules-unit-testing

# Run tests
npm test
```

## 📈 Monitoring and Maintenance

### 1. Security Rules Monitoring
- Monitor Firebase Console for rule violations
- Set up alerts for unusual access patterns
- Review logs regularly

### 2. Rule Updates
- Test rule changes in development first
- Deploy incrementally
- Monitor after deployment

### 3. Performance Monitoring
- Watch for slow rule evaluations
- Optimize complex rule conditions
- Consider denormalizing data if needed

## 🆘 Troubleshooting

### Common Issues

1. **Permission Denied Errors**
   - Check user authentication status
   - Verify user has proper permissions
   - Check data structure matches validation rules

2. **File Upload Failures**
   - Verify file type is allowed
   - Check file size limits
   - Ensure user owns the upload path

3. **Chat Access Issues**
   - Verify user is chat participant
   - Check for blocking relationships
   - Ensure chat exists in database

### Debug Steps
1. Check Firebase Console logs
2. Verify user authentication
3. Test rules in Firebase playground
4. Review data structure requirements

## 📞 Support

For issues with these security rules:
1. Check Firebase documentation
2. Review the troubleshooting section
3. Test with minimal data sets
4. Use Firebase emulators for local testing

Remember: Security rules are your first line of defense. Always validate data on both client and server sides!