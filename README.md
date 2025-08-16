# PingMe - WhatsApp-like Chat Application

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

### 📢 Additional Features
- **Status Updates**: 24-hour status feature like WhatsApp Stories
- **Broadcast Lists**: Send messages to multiple contacts
- **Search**: Search through messages and contacts
- **Chat Management**: Clear chat, delete chat with proper user isolation
- **Message Search**: Find specific messages across all chats
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

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 24+ (Android 7.0)
- Firebase project with authentication enabled
- Cloudinary account for media storage

### Setup Instructions

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

4. **Cloudinary Setup**
   - Create account at https://cloudinary.com
   - Update `build.gradle` with your Cloudinary credentials:
   ```gradle
   buildConfigField "String", "CLOUDINARY_CLOUD_NAME", '"your-cloud-name"'
   buildConfigField "String", "CLOUDINARY_API_KEY", '"your-api-key"'
   buildConfigField "String", "CLOUDINARY_API_SECRET", '"your-api-secret"'
   ```

5. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ```

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

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by WhatsApp's user experience
- Firebase for backend infrastructure
- Cloudinary for media management
- Material Design for UI components
- Open source Android community

## 📱 Screenshots

> Add screenshots of your app here showing key features like chat interface, profile screens, etc.

## 🐛 Known Issues

- Message status synchronization may have delays in poor network conditions
- Large media files might take time to upload on slow connections
- Some features require proper Firebase security rules configuration

## 🔄 Future Enhancements

- End-to-end encryption
- Voice/video calling
- Group chats
- Message reactions
- Location sharing
- Sticker support
- Message scheduling

---

**Note**: This is a demo application. For production use, implement proper security measures, rate limiting, and comprehensive error handling.