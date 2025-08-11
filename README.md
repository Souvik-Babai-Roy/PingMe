# PingMe - WhatsApp-like Messaging App

A comprehensive Android messaging application built with modern architecture and professional features, designed to provide a WhatsApp-like experience with enhanced functionality.

## 🚀 Features

### 🔐 Authentication & Security
- **Google Sign-In**: Seamless authentication using Google accounts
- **Email Registration/Login**: Traditional email-based authentication
- **Forgot Password**: Email-based password reset functionality
- **End-to-End Encryption**: RSA/AES encryption for secure messaging
- **Privacy Controls**: Granular privacy settings for user data

### 👤 User Profile & Settings
- **Profile Setup**: Name, profile picture, and bio customization
- **Privacy Settings**: Control visibility of profile photo, last seen, about, and read receipts
- **Theme Support**: Light, dark, and auto theme modes
- **Account Management**: Profile editing and account settings

### 💬 Messaging Features

#### Core Messaging
- **1-on-1 Private Chats**: Individual conversations with friends
- **Real-time Messaging**: Instant message delivery using Firebase Realtime Database
- **Message Types**: Text, images, videos, audio, and documents
- **Multiple Media Selection**: Select and send multiple images/videos at once
- **Inline Previews**: Direct preview of images and videos in chat

#### Message Actions
- **Reply**: Reply to specific messages with context
- **Forward**: Forward messages to other contacts
- **Edit**: Edit sent text messages (with edit indicator)
- **Delete**: Delete messages for yourself or everyone (within time limit)
- **Message Search**: Search through messages by text, contact, or date

#### Message Status Indicators
- ✅ **Single tick**: Message sent
- ✅✅ **Double tick**: Message delivered
- 🔵🔵 **Blue double tick**: Message read (respects privacy settings)

### 📢 Broadcast Lists
- **Create Broadcast Lists**: Send messages to multiple contacts individually
- **Manage Recipients**: Add/remove contacts from broadcast lists
- **Broadcast History**: View and manage broadcast conversations
- **Individual Delivery**: Each recipient receives the message as a personal chat

### 🔍 Advanced Search
- **Message Search**: Search through all conversations
- **Contact Search**: Find users by name, email, or phone number
- **Date Filtering**: Filter search results by date ranges
- **Real-time Results**: Instant search results as you type

### 👥 Social Features
- **Add Friends**: Search and add new contacts
- **Friend Management**: View and manage your friend list
- **Block/Unblock**: Block unwanted contacts with privacy controls
- **Online Status**: Real-time online/offline indicators
- **Last Seen**: Timestamp of last activity (with privacy controls)

### 🔔 Real-time Features
- **Typing Indicators**: "User is typing..." notifications
- **Online Presence**: Real-time online status updates
- **Push Notifications**: Instant notifications for new messages
- **Read Receipts**: Message read status (configurable)

### 🎨 User Experience
- **Modern UI**: Material Design 3 components
- **Smooth Animations**: Fluid transitions and interactions
- **Responsive Design**: Optimized for different screen sizes
- **Accessibility**: Support for accessibility features

## 🏗️ Technical Architecture

### Frontend
- **Language**: Java
- **UI Framework**: Android SDK with Material Design
- **Architecture**: MVVM with Data Binding
- **Navigation**: Android Navigation Component
- **Image Loading**: Glide for efficient image handling

### Backend & Services
- **Authentication**: Firebase Authentication
- **Database**: 
  - Firestore (user data, settings, friends)
  - Firebase Realtime Database (messages, chats, presence)
- **Storage**: Cloudinary for media file storage
- **Push Notifications**: Firebase Cloud Messaging
- **Encryption**: Custom RSA/AES implementation

### Key Libraries
- Firebase BOM (Authentication, Firestore, Realtime Database, Cloud Messaging)
- Cloudinary Android SDK
- Google Play Services (Sign-In)
- Glide (Image loading)
- Material Design Components
- Dexter (Permissions)

## 📱 Screenshots

*[Screenshots would be added here]*

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (API level 24)
- Google Play Services
- Firebase project setup

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/pingme-android.git
   cd pingme-android
   ```

2. **Firebase Setup**
   - Create a new Firebase project
   - Enable Authentication, Firestore, Realtime Database, and Cloud Messaging
   - Download `google-services.json` and place it in the `app/` directory
   - Configure Firestore and Realtime Database rules

3. **Cloudinary Setup**
   - Create a Cloudinary account
   - Update the Cloudinary credentials in `app/build.gradle`

4. **Build and Run**
   ```bash
   ./gradlew build
   ```

### Configuration

#### Firebase Configuration
1. Enable Google Sign-In in Firebase Console
2. Set up Firestore security rules
3. Configure Realtime Database rules
4. Set up Cloud Messaging

#### Cloudinary Configuration
Update the following in `app/build.gradle`:
```gradle
buildConfigField "String", "CLOUDINARY_CLOUD_NAME", '"your_cloud_name"'
buildConfigField "String", "CLOUDINARY_API_KEY", '"your_api_key"'
buildConfigField "String", "CLOUDINARY_API_SECRET", '"your_api_secret"'
```

## 🔧 Development

### Project Structure
```
app/src/main/java/com/pingme/android/
├── activities/          # Activity classes
├── adapters/           # RecyclerView adapters
├── fragments/          # Fragment classes
├── models/             # Data models
├── repositories/       # Data repositories
├── services/           # Background services
├── utils/              # Utility classes
└── viewmodels/         # ViewModel classes
```

### Key Components

#### Models
- `User`: User profile and settings
- `Message`: Message data with actions and metadata
- `Chat`: Chat conversation data
- `Broadcast`: Broadcast list management

#### Services
- `PingMeFirebaseMessagingService`: Push notification handling
- `EncryptionUtil`: End-to-end encryption utilities

#### Utils
- `FirestoreUtil`: Database operations and queries
- `CloudinaryUtil`: Media upload and management
- `NotificationUtil`: Push notification utilities
- `PreferenceUtils`: User preferences management

## 🔒 Security Features

### End-to-End Encryption
- RSA key pairs for each user
- AES encryption for message content
- Secure key exchange mechanism
- Encrypted message storage

### Privacy Controls
- Profile photo visibility
- Last seen privacy
- Read receipts toggle
- About/bio visibility
- Block/unblock functionality

## 📊 Performance Optimizations

- Efficient image loading with Glide
- Lazy loading for chat messages
- Optimized database queries
- Background message processing
- Memory-efficient media handling

## 🧪 Testing

The app includes comprehensive testing:
- Unit tests for core functionality
- Integration tests for Firebase operations
- UI tests for critical user flows

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Firebase team for the excellent backend services
- Material Design team for the UI components
- Open source community for various libraries

## 📞 Support

For support and questions:
- Create an issue in the GitHub repository
- Contact the development team
- Check the documentation

---

**PingMe** - Professional messaging experience with privacy and security at its core.