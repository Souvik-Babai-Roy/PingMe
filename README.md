<div align="center">
   
# 📱 PingMe - Modern Chat Application
   
</div>
<div align="center">

<img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/drawable/logo.png" alt="PingMe Logo" width="520"/>


**A feature-rich Android messaging app with real-time chat, media sharing, and privacy controls**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-orange.svg)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[📱 Download APK](#-download) • [✨ Features](#-features) • [🛠 Setup](#-setup-for-developers) • [📸 Screenshots](#-screenshots)

</div>

---

## 📱 Download

### Latest Release

**[⬇️ Download PingMe v1.0.0 APK](../../releases/latest/download/pingme.apk)**

> **System Requirements:** Android 7.0 (API 24) or higher

### Installation Steps

1. **Enable Unknown Sources** (if not already enabled):
   - Go to `Settings` → `Security` → `Unknown Sources`
   - Or `Settings` → `Apps & notifications` → `Special app access` → `Install unknown apps`

2. **Download & Install**:
   - Download the APK from the link above
   - Open the downloaded file
   - Tap "Install" when prompted

3. **First Launch**:
   - Open PingMe
   - Sign in with Google or create an account
   - Set up your profile
   - Start chatting!

### All Releases
View all releases and changelog: **[📋 Releases Page](../../releases)**

---

PingMe is a modern Android messaging application inspired by WhatsApp, built with Firebase backend and featuring comprehensive chat functionality, media sharing, and privacy controls.

## ✨ Features

### 🔐 Authentication & Security
- **Google Sign-In**: One-tap authentication with Google
- **Email/Password**: Traditional email authentication with password reset
- **Profile Setup**: Comprehensive user profile with image upload

### 👥 Contact Management
- **Add Friends**: Search users by email and send friend requests
- **Contact Integration**: WhatsApp-like contact discovery
- **Friend Management**: View, add, and remove friends
- **Block/Unblock**: Privacy controls for unwanted contacts
- **Profile Privacy**: Control who can see your profile photo, last seen, and about

### 💬 WhatsApp-like Chat Features
- **Real-time Messaging**: Instant message delivery using Firebase Realtime Database
- **Message Status**: 
  - ✓ **Single tick**: Message sent
  - ✓✓ **Double tick**: Message delivered  
  - 🔵🔵 **Blue double tick**: Message read (respects privacy settings)
- **Media Sharing**: Send images, videos, audio, and documents
- **Message Actions**: Forward, edit, delete, reply
- **Typing Indicators**: See when someone is typing
- **Online Status**: Real-time online/offline indicators
- **Last Seen**: WhatsApp-like last seen functionality with privacy controls

### 📱 WhatsApp-like UI/UX
- **Chat List**: Shows recent chats with last message preview and status
- **Individual Chats**: Clean chat interface with message bubbles
- **Profile Pictures**: Circular profile images with online indicators
- **Time Stamps**: Relative time formatting (today, yesterday, etc.)
- **Unread Badges**: Visual indicators for unread messages
- **Material Design**: Modern Android UI with purple color scheme

### 📁 Media & File Handling
- **Cloudinary Integration**: Secure cloud storage for all media
- **Image Sharing**: Send and view images with full-screen viewer
- **Video Sharing**: Video messages with thumbnail previews
- **Audio Messages**: Voice messages with playback controls
- **Document Sharing**: Send and receive various file types
- **Image Compression**: Optimized image uploads

### 🔒 Privacy & Security
- **Profile Photo Visibility**: Control who can see your profile picture
- **Last Seen Privacy**: Hide/show last seen status
- **About Privacy**: Control about section visibility
- **Read Receipts**: Enable/disable blue ticks
- **Block Users**: Comprehensive blocking system
- **Message Encryption**: Secure message handling

### 🔍 Search & Navigation
- **Global Message Search**: Find messages across all chats with highlighting
- **Smart Search Results**: Search by content, contact name, or date range
- **Centered Message View**: Click search results to jump directly to messages
- **Search Highlighting**: Found text is highlighted with context
- **Date Filtering**: Filter search results by time periods
- **Contact Search**: Quick contact discovery and management

### 📢 Additional Features
- **Status Updates**: 24-hour status feature like WhatsApp Stories
- **Broadcast Lists**: Send messages to multiple contacts
- **Chat Management**: Clear chat, delete chat with proper user isolation
- **Forward Messages**: Share messages with other contacts
- **Notifications**: Push notifications for new messages
- **Dark/Light Themes**: Customizable app appearance

## 🛠 Technical Architecture

### Backend
- **Firebase Authentication**: User management and security
- **Firebase Firestore**: User profiles, settings, and persistent data
- **Firebase Realtime Database**: Real-time messaging and presence
- **Firebase Cloud Messaging**: Push notifications
- **Cloudinary**: Media storage and management

### Frontend
- **Material Design**: Modern Android UI components
- **Data Binding**: Efficient view binding
- **ViewPager2**: Smooth tab navigation
- **RecyclerView**: Optimized list performance
- **Glide**: Image loading and caching
- **CircleImageView**: Profile picture handling

### Key Components
- **FirebaseUtil**: Centralized Firebase operations
- **CloudinaryUtil**: Media upload and management
- **MessageAdapter**: WhatsApp-like message display
- **ChatListAdapter**: Optimized chat list with status indicators
- **PreferenceUtils**: Settings and privacy management

## 🚀 Setup for Developers

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 24+ (Android 7.0)
- Firebase project with authentication enabled
- Cloudinary account for media storage
- Git for version control

### Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd PingMe
   ```

2. **Android SDK Setup**
   - Download and install Android Studio
   - Open the project to download required SDK components
   - Update `local.properties` with your SDK path:
   ```properties
   # For Linux
   sdk.dir=/home/YourUsername/Android/Sdk
   
   # For Windows
   sdk.dir=C:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
   
   # For macOS
   sdk.dir=/Users/YourUsername/Library/Android/sdk
   ```

3. **Firebase Configuration**
   - Create a new Firebase project at https://console.firebase.google.com
   - Enable Authentication (Email/Password and Google)
   - Enable Firestore Database
   - Enable Realtime Database
   - Download `google-services.json` and place it in `app/`

4. **Environment Configuration**
   - Copy the environment template:
   ```bash
   cp .env.example .env
   ```
   - Create account at https://cloudinary.com and get your credentials
   - Update `.env` file with your actual values:
   ```env
   CLOUDINARY_CLOUD_NAME=your-cloud-name
   CLOUDINARY_API_KEY=your-api-key
   CLOUDINARY_API_SECRET=your-api-secret
   FIREBASE_PROJECT_ID=your-firebase-project-id
   ```

5. **Build and Run**
   ```bash
   # For development
   ./gradlew assembleDebug
   
   # For release APK
   ./gradlew assembleRelease
   ```

### 📦 Building APK for Distribution

To create a release APK:

1. **Generate Signed APK**:
   - In Android Studio: `Build` → `Generate Signed Bundle/APK`
   - Or use command line with your keystore:
   ```bash
   ./gradlew assembleRelease
   ```

2. **APK Location**:
   - Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   - Release APK: `app/build/outputs/apk/release/app-release.apk`

3. **Testing**:
   - Test on multiple devices and Android versions
   - Verify all features work correctly
   - Test with different network conditions

## 🏗 Project Structure

```
app/src/main/java/com/pingme/android/
├── activities/          # Activity classes
│   ├── MainActivity.java
│   ├── ChatActivity.java
│   ├── AuthActivity.java
│   └── ...
├── adapters/           # RecyclerView adapters
│   ├── MessageAdapter.java
│   ├── ChatListAdapter.java
│   └── ...
├── fragments/          # Fragment classes
│   ├── ChatsFragment.java
│   ├── StatusFragment.java
│   └── ...
├── models/             # Data models
│   ├── User.java
│   ├── Message.java
│   ├── Chat.java
│   └── ...
├── utils/              # Utility classes
│   ├── FirebaseUtil.java
│   ├── CloudinaryUtil.java
│   └── ...
└── services/           # Background services
    └── FCMService.java
```

## 🎨 Design System

### Color Scheme
- **Primary**: Purple (#7C4DFF)
- **Secondary**: Soft Lavender (#9575CD)
- **Background**: Light Purple (#F3E5F5)
- **Text**: Dark Gray (#1A1A1A)
- **Message Bubbles**: Purple gradient for sent, white for received

### Key UI Patterns
- **Chat Bubbles**: Rounded corners with different colors for sent/received
- **Status Indicators**: Single/double/blue ticks for message status
- **Profile Images**: Circular with online status indicators
- **Tab Navigation**: Bottom navigation with smooth transitions
- **Material Components**: Cards, FABs, and modern input fields

## 🚀 Creating GitHub Releases

### For Maintainers: Publishing APK Releases

1. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

2. **Create GitHub Release**:
   - Go to your repository on GitHub
   - Click "Releases" → "Create a new release"
   - Tag version (e.g., `v1.0.0`)
   - Release title: `PingMe v1.0.0`
   - Describe changes and new features
   - Attach the APK file: `app/build/outputs/apk/release/app-release.apk`
   - Rename to: `pingme-v1.0.0.apk`

3. **Release Notes Template**:
   ```markdown
   ## 🎉 PingMe v1.0.0
   
   ### ✨ New Features
   - Feature 1
   - Feature 2
   
   ### 🐛 Bug Fixes  
   - Fix 1
   - Fix 2
   
   ### 📱 Download
   - **APK Size**: ~XX MB
   - **Min Android**: 7.0 (API 24)
   - **Target Android**: 14 (API 34)
   ```

## 🤝 Contributing

We welcome contributions! Here's how to get started:

1. **Fork the repository**
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Make your changes** with proper testing
4. **Commit your changes** (`git commit -m 'Add amazing feature'`)
5. **Push to the branch** (`git push origin feature/amazing-feature`)
6. **Open a Pull Request** with detailed description

### 🔧 Development Guidelines

- Follow Android development best practices
- Write clean, documented code
- Test on multiple devices/screen sizes
- Update README if adding new features
- Ensure Firebase rules are properly configured
- **Never commit sensitive keys** - use `.env` file
- Keep `google-services.json` secure and out of version control

### 🔐 Security Best Practices

- **Environment Variables**: All sensitive keys are stored in `.env` file (not committed)
- **Firebase Config**: `google-services.json` is excluded from version control
- **API Keys**: Cloudinary and other API keys are loaded from environment
- **Keystore**: Signing keys are kept separate and secure
- **Template**: Use `.env.example` as a template for new developers

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by WhatsApp's user experience
- Firebase for backend infrastructure
- Cloudinary for media management
- Material Design for UI components
- Open source Android community

## 📸 Screenshots

<div align="center">

### 🏠 Main Interface
| Chat List | Individual Chat | Search Results |
|-----------|----------------|----------------|
| <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Chatlist.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Chat.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Search.jpg" width="200"/> |

### 👤 Profile & Settings
| Login | Privacy Settings | Status Updates |
|-------|------------------|----------------|
| <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Login.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Settings.jpg" width="200"/> | <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Status.jpg" width="200"/> |

### 📁 Media Sharing
| Image / Media Sharing |
|------------------------|
| <img src="https://raw.githubusercontent.com/Souvik-Babai-Roy/PingMe/main/app/src/main/res/screenshots/Attachment.jpg" width="200"/> |
</div>


## 🐛 Known Issues & Limitations

- **Network Dependency**: Message status synchronization may have delays in poor network conditions
- **Media Upload**: Large media files might take time to upload on slow connections  
- **Firebase Setup**: Some features require proper Firebase security rules configuration
- **Storage**: Media files are stored in cloud (Cloudinary) - requires internet for viewing
- **Real-time Features**: Typing indicators and online status require active internet connection

## ✅ Recent Improvements

- **Fixed**: Search result navigation now centers messages properly (no longer hidden under toolbar)
- **Fixed**: Auto-scroll behavior after search navigation - stays focused on found message
- **Enhanced**: Message search with better highlighting and date filtering
- **Improved**: Chat performance with optimized message loading
- **Added**: Smart message positioning for better user experience

## 🔄 Future Enhancements

- End-to-end encryption
- Voice/video calling
- Group chats
- Message reactions
- Location sharing
- Sticker support
- Message scheduling

---

## 📞 Support & Contact

- **Issues**: [GitHub Issues](../../issues)
- **Discussions**: [GitHub Discussions](../../discussions)
- **Email**: souvikroy733@gmail.com

## ⭐ Show Your Support

If you like this project, please consider:
- ⭐ **Star** this repository
- 🍴 **Fork** it for your own projects
- 📢 **Share** with others
- 🐛 **Report** bugs and suggest features

## 📊 Project Stats

![GitHub stars](https://img.shields.io/github/stars/Souvik-Babai-Roy/PingMe?style=social)
![GitHub forks](https://img.shields.io/github/forks/Souvik-Babai-Roy/PingMe?style=social)
![GitHub issues](https://img.shields.io/github/issues/Souvik-Babai-Roy/PingMe)
![GitHub pull requests](https://img.shields.io/github/issues-pr/Souvik-Babai-Roy/PingMe)

---

<div align="center">

**Built with ❤️ for the Android community**

*This is a demonstration application showcasing modern Android development practices.  
For production use, implement additional security measures, rate limiting, and comprehensive error handling.*

**[⬆ Back to Top](#-pingme---modern-whatsapp-like-chat-application)**

</div>
