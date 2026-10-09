// VoiceAssistant.jsx - FINAL VERSION with fixed sound + updated help popup
import React, { useState, useEffect, useRef } from 'react';
import { Mic, MicOff, X, Volume2, VolumeX, HelpCircle, Search, Briefcase, MapPin, DollarSign, Clock } from 'lucide-react';
import api from '../services/api';
import authService from '../services/auth';
import { useNavigate } from 'react-router-dom';
import JobApplicationModal from './JobApplicationModal';
import { extractStarRating, resolveJobFromCommand } from '../utils/voiceCommandParsing';

// Language configuration for 3 languages
const LANGUAGES = {
  'en-US': { 
    name: 'English', 
    recognitionCode: 'en-US',
    voiceNames: ['Google UK English', 'Microsoft David', 'Google US English']
  },
  'hi-IN': { 
    name: 'हिन्दी', 
    recognitionCode: 'hi-IN',
    voiceNames: ['Google हिन्दी', 'Microsoft Hemant', 'Hindi India']
  },
  'mr-IN': { 
    name: 'मराठी', 
    recognitionCode: 'mr-IN',
    voiceNames: ['Google मराठी', 'Marathi India']
  }
};

// Command mappings for all three languages
const COMMAND_MAPPINGS = {
  'recommend jobs': {
    'en-US': ['recommend jobs', 'recommendations', 'job recommendations', 'suggest jobs'],
    'hi-IN': ['नौकरी सुझाएं', 'अनुशंसा', 'सुझाव'],
    'mr-IN': ['नोकरी सुचवा', 'शिफारस', 'सूचना']
  },
  'browse jobs': {
    'en-US': ['browse jobs', 'show jobs', 'view jobs', 'jobs page', 'all jobs'],
    'hi-IN': ['नौकरियां दिखाएं', 'सभी नौकरियां', 'जॉब पेज'],
    'mr-IN': ['नोकऱ्या दाखवा', 'सर्व नोकऱ्या', 'जॉब पेज']
  },
  'search jobs': {
    'en-US': ['search jobs', 'find jobs', 'look for jobs', 'job search'],
    'hi-IN': ['नौकरी खोजें', 'नौकरी ढूंढो', 'जॉब सर्च'],
    'mr-IN': ['नोकरी शोधा', 'नोकऱ्या शोधा', 'जॉब सर्च']
  },
  'my applications': {
    'en-US': ['my applications', 'my jobs', 'applied jobs', 'application status', 'my applied jobs', 'applications'],
    'hi-IN': ['मेरे आवेदन', 'मेरी नौकरियां', 'आवेदन स्थिति', 'मेरे अप्लाई', 'अप्लाइड जॉब्स'],
    'mr-IN': ['माझे अर्ज', 'माझ्या नोकऱ्या', 'अर्ज स्थिती', 'माझे अप्लाय', 'अप्लाइड नोकऱ्या']
  },
  'go to dashboard': {
    'en-US': ['go to dashboard', 'open dashboard', 'my dashboard', 'dashboard', 'show dashboard'],
    'hi-IN': ['डैशबोर्ड खोलें', 'मेरा डैशबोर्ड', 'डैशबोर्ड दिखाओ', 'डैशबोर्ड'],
    'mr-IN': ['डॅशबोर्ड उघडा', 'माझा डॅशबोर्ड', 'डॅशबोर्ड दाखवा', 'डॅशबोर्ड']
  },
  'upload resume': {
    'en-US': ['upload resume', 'upload cv', 'upload my resume', 'add resume', 'new resume'],
    'hi-IN': ['रिज्यूमे अपलोड करें', 'सीवी अपलोड करें', 'रिज्यूमे जोड़ें', 'बायोडाटा अपलोड'],
    'mr-IN': ['रिझ्यूमे अपलोड करा', 'सीव्ही अपलोड करा', 'रिझ्यूमे जोडा', 'बायोडाटा अपलोड']
  },
  'go to home': {
    'en-US': ['go to home', 'home', 'go home', 'open home', 'main page', 'home page'],
    'hi-IN': ['होम पेज', 'होम', 'मुख्य पृष्ठ', 'होम पेज खोलें'],
    'mr-IN': ['होम पेज', 'होम', 'मुख्य पृष्ठ', 'होम पेज उघडा']
  },
  'help': {
    'en-US': ['help', 'what can you do', 'how to use', 'commands', 'support'],
    'hi-IN': ['मदद', 'क्या कर सकते हो', 'कैसे उपयोग करें', 'आदेश', 'सहायता'],
    'mr-IN': ['मदत', 'काय करू शकता', 'कसे वापरावे', 'आदेश', 'सहाय्य']
  },
  'hello': {
    'en-US': ['hello', 'hi', 'hey', 'good morning', 'good afternoon', 'good evening'],
    'hi-IN': ['नमस्ते', 'नमस्कार', 'हाय', 'हेलो', 'सुप्रभात', 'शुभ संध्या'],
    'mr-IN': ['नमस्कार', 'हाय', 'हेलो', 'शुभ प्रभात', 'शुभ संध्या']
  },
  // Available to every role.
  'my profile': {
    'en-US': ['my profile', 'open my profile', 'view my profile', 'profile page', 'go to profile'],
    'hi-IN': ['मेरी प्रोफाइल', 'प्रोफाइल खोलें', 'प्रोफाइल पेज'],
    'mr-IN': ['माझी प्रोफाइल', 'प्रोफाइल उघडा', 'प्रोफाइल पेज']
  },
  'logout': {
    'en-US': ['log out', 'logout', 'sign out', 'log me out'],
    'hi-IN': ['लॉग आउट करें', 'साइन आउट करें', 'लॉगआउट'],
    'mr-IN': ['लॉग आउट करा', 'साइन आउट करा', 'लॉगआउट']
  },
  // Recruiter-only.
  'post job': {
    'en-US': ['post a job', 'post job', 'create a job', 'new job posting', 'add a job'],
    'hi-IN': ['नौकरी पोस्ट करें', 'नई नौकरी जोड़ें'],
    'mr-IN': ['नोकरी पोस्ट करा', 'नवीन नोकरी जोडा']
  },
  'discover candidates': {
    'en-US': ['discover candidates', 'find candidates', 'search candidates', 'candidate search'],
    'hi-IN': ['उम्मीदवार खोजें', 'कैंडिडेट खोजें'],
    'mr-IN': ['उमेदवार शोधा', 'कँडिडेट शोधा']
  },
  // Shared phrasing; routed to the recruiter's or admin's own analytics view by role.
  'view analytics': {
    'en-US': ['show analytics', 'view analytics', 'my analytics', 'hiring analytics', 'open analytics'],
    'hi-IN': ['एनालिटिक्स दिखाएं', 'विश्लेषण दिखाएं'],
    'mr-IN': ['अॅनालिटिक्स दाखवा', 'विश्लेषण दाखवा']
  },
  // Admin-only.
  'pending moderation': {
    'en-US': ['pending jobs', 'moderation queue', 'pending reviews', 'review queue', 'pending approvals'],
    'hi-IN': ['लंबित नौकरियां', 'मॉडरेशन क्यू'],
    'mr-IN': ['प्रलंबित नोकऱ्या', 'मॉडरेशन क्यू']
  },
  // Stops speech mid-sentence without closing the assistant - checked before the generic
  // "unknown command" fallback so it's usable regardless of what's currently being said.
  'stop': {
    'en-US': ['stop talking', 'stop speaking', 'be quiet', 'stop listening to that', 'stop'],
    'hi-IN': ['बोलना बंद करो', 'रुको', 'चुप रहो'],
    'mr-IN': ['बोलणे थांबवा', 'थांबा', 'गप्प राहा']
  },
  // Re-speaks the last assistant message instead of making the user ask the whole question again.
  'repeat': {
    'en-US': ['say that again', 'repeat that', 'repeat', 'say it again', 'what did you say'],
    'hi-IN': ['फिर से कहो', 'दोहराओ', 'फिर बोलो'],
    'mr-IN': ['पुन्हा सांगा', 'पुन्हा बोला']
  }
};

// Complete command list for help popup with click handlers - UPDATED with all commands
const ALL_COMMANDS = {
  'en-US': [
    { key: 'job_search', category: '🔍 Job Search', commands: [
      { text: '• "recommend jobs" - Go to jobs page with recommendations', action: 'recommend jobs' },
      { text: '• "browse jobs" - Go to jobs page', action: 'browse jobs' },
      { text: '• "search jobs" - Search for jobs', action: 'search jobs' },
      { text: '• "find software developer jobs" - Search by job title', action: 'search_by_title' },
      { text: '• "looking for data scientist" - Find specific roles', action: 'search_by_title' },
      { text: '• "devops engineer positions" - Any job title works!', action: 'search_by_title' }
    ]},
    { category: '📋 Applications', commands: [
      { text: '• "my applications" - Check your application status', action: 'my applications' },
      { text: '• "application status" - View applied jobs', action: 'my applications' },
      { text: '• "my jobs" - See your job applications', action: 'my applications' }
    ]},
    { key: 'job_actions', category: '🎯 Job Actions (after a search)', commands: [
      { text: '• "apply to job 1" / "apply to the first job" - Apply to a listed job', action: 'apply_to_job' },
      { text: '• "rate job 1 five stars" - Rate a listed job', action: 'rate_job' }
    ]},
    { category: '🧭 Navigation', commands: [
      { text: '• "go to dashboard" - Open your dashboard', action: 'go to dashboard' },
      { text: '• "upload resume" - Go to resume upload page', action: 'upload resume' },
      { text: '• "go to home" - Return to home page', action: 'go to home' }
    ]},
    { category: '❓ Help', commands: [
      { text: '• "help" - Show this help menu', action: 'help' },
      { text: '• "commands" - List all commands', action: 'help' },
      { text: '• "what can you do" - Learn about features', action: 'help' }
    ]},
    { category: '👋 Greetings', commands: [
      { text: '• "hello" / "hi" - Greet the assistant', action: 'hello' },
      { text: '• "good morning" / "good evening" - Time-based greetings', action: 'hello' }
    ]}
  ],
  
  'hi-IN': [
    { key: 'job_search', category: '🔍 नौकरी खोज', commands: [
      { text: '• "recommend jobs" - अनुशंसाओं के साथ जॉब पेज पर जाएं', action: 'recommend jobs' },
      { text: '• "browse jobs" - जॉब पेज पर जाएं', action: 'browse jobs' },
      { text: '• "search jobs" - नौकरियां खोजें', action: 'search jobs' },
      { text: '• "सॉफ्टवेयर डेवलपर नौकरी" - शीर्षक से खोजें', action: 'search_by_title' },
      { text: '• "डाटा साइंटिस्ट के लिए" - विशिष्ट भूमिकाएं खोजें', action: 'search_by_title' },
      { text: '• "डेवॉप्स इंजीनियर पद" - कोई भी शीर्षक काम करता है', action: 'search_by_title' }
    ]},
    { category: '📋 आवेदन', commands: [
      { text: '• "मेरे आवेदन" - आपके आवेदन की स्थिति जांचें', action: 'my applications' },
      { text: '• "आवेदन स्थिति" - अप्लाइड जॉब्स देखें', action: 'my applications' },
      { text: '• "मेरी नौकरियां" - आपके नौकरी आवेदन देखें', action: 'my applications' }
    ]},
    { key: 'job_actions', category: '🎯 नौकरी क्रियाएं (खोज के बाद)', commands: [
      { text: '• "apply to job 1" - सूची से किसी नौकरी के लिए आवेदन करें', action: 'apply_to_job' },
      { text: '• "rate job 1 five stars" - सूची से किसी नौकरी को रेट करें', action: 'rate_job' }
    ]},
    { category: '🧭 नेविगेशन', commands: [
      { text: '• "डैशबोर्ड खोलें" - अपना डैशबोर्ड खोलें', action: 'go to dashboard' },
      { text: '• "रिज्यूमे अपलोड करें" - रिज्यूमे अपलोड पेज पर जाएं', action: 'upload resume' },
      { text: '• "होम पेज" - मुख्य पृष्ठ पर जाएं', action: 'go to home' }
    ]},
    { category: '❓ सहायता', commands: [
      { text: '• "मदद" - यह मेनू दिखाएं', action: 'help' },
      { text: '• "आदेश" - सभी कमांड की सूची', action: 'help' },
      { text: '• "क्या कर सकते हो" - फीचर्स के बारे में जानें', action: 'help' }
    ]},
    { category: '👋 अभिवादन', commands: [
      { text: '• "नमस्ते" / "हाय" - असिस्टेंट को नमस्कार करें', action: 'hello' },
      { text: '• "सुप्रभात" / "शुभ संध्या" - समय के अनुसार अभिवादन', action: 'hello' }
    ]}
  ],
  
  'mr-IN': [
    { key: 'job_search', category: '🔍 नोकरी शोध', commands: [
      { text: '• "recommend jobs" - शिफारशींसह जॉब पेजवर जा', action: 'recommend jobs' },
      { text: '• "browse jobs" - जॉब पेजवर जा', action: 'browse jobs' },
      { text: '• "search jobs" - नोकऱ्या शोधा', action: 'search jobs' },
      { text: '• "सॉफ्टवेअर डेव्हलपर नोकरी" - शीर्षकाने शोधा', action: 'search_by_title' },
      { text: '• "डेटा सायंटिस्ट साठी" - विशिष्ट भूमिका शोधा', action: 'search_by_title' },
      { text: '• "डेव्हॉप्स इंजिनियर पद" - कोणतेही शीर्षक कार्य करते', action: 'search_by_title' }
    ]},
    { category: '📋 अर्ज', commands: [
      { text: '• "माझे अर्ज" - तुमच्या अर्जांची स्थिती तपासा', action: 'my applications' },
      { text: '• "अर्ज स्थिती" - अप्लाइड नोकऱ्या पहा', action: 'my applications' },
      { text: '• "माझ्या नोकऱ्या" - तुमचे नोकरी अर्ज पहा', action: 'my applications' }
    ]},
    { key: 'job_actions', category: '🎯 नोकरी क्रिया (शोधल्यानंतर)', commands: [
      { text: '• "apply to job 1" - यादीतील नोकरीसाठी अर्ज करा', action: 'apply_to_job' },
      { text: '• "rate job 1 five stars" - यादीतील नोकरीला रेट करा', action: 'rate_job' }
    ]},
    { category: '🧭 नेव्हिगेशन', commands: [
      { text: '• "डॅशबोर्ड उघडा" - तुमचा डॅशबोर्ड उघडा', action: 'go to dashboard' },
      { text: '• "रिझ्यूमे अपलोड करा" - रिझ्यूमे अपलोड पेजवर जा', action: 'upload resume' },
      { text: '• "होम पेज" - मुख्य पृष्ठावर जा', action: 'go to home' }
    ]},
    { category: '❓ मदत', commands: [
      { text: '• "मदत" - हे मेनू दाखवा', action: 'help' },
      { text: '• "आदेश" - सर्व कमांडची यादी', action: 'help' },
      { text: '• "काय करू शकता" - फीचर्सबद्दल जाणून घ्या', action: 'help' }
    ]},
    { category: '👋 अभिवादन', commands: [
      { text: '• "नमस्कार" / "हाय" - सहाय्यकाला नमस्कार करा', action: 'hello' },
      { text: '• "शुभ प्रभात" / "शुभ संध्या" - वेळेनुसार अभिवादन', action: 'hello' }
    ]}
  ]
};

// Commands only a recruiter or admin account can use, appended onto ALL_COMMANDS by
// getCommandSections() below - the job-search/apply/rate sections above are candidate-only and
// are filtered back out for those two roles, since they hit endpoints a recruiter/admin account
// can't meaningfully use.
const RECRUITER_COMMANDS = {
  'en-US': { key: 'recruiter', category: '🧑‍💼 Recruiter Tools', commands: [
    { text: '• "post a job" - Open your jobs tab to create one', action: 'post job' },
    { text: '• "discover candidates" - Browse open-to-work candidates', action: 'discover candidates' },
    { text: '• "view applications" - See applicants on your jobs', action: 'my applications' },
    { text: '• "show analytics" - Open your hiring analytics', action: 'view analytics' },
  ]},
  'hi-IN': { key: 'recruiter', category: '🧑‍💼 रिक्रूटर टूल्स', commands: [
    { text: '• "post a job" - नौकरी बनाने के लिए जॉब्स टैब खोलें', action: 'post job' },
    { text: '• "discover candidates" - उम्मीदवार खोजें', action: 'discover candidates' },
    { text: '• "view applications" - अपनी नौकरियों के आवेदक देखें', action: 'my applications' },
    { text: '• "show analytics" - अपनी हायरिंग एनालिटिक्स खोलें', action: 'view analytics' },
  ]},
  'mr-IN': { key: 'recruiter', category: '🧑‍💼 रिक्रूटर टूल्स', commands: [
    { text: '• "post a job" - नोकरी तयार करण्यासाठी जॉब्स टॅब उघडा', action: 'post job' },
    { text: '• "discover candidates" - उमेदवार शोधा', action: 'discover candidates' },
    { text: '• "view applications" - तुमच्या नोकऱ्यांचे अर्जदार पहा', action: 'my applications' },
    { text: '• "show analytics" - तुमची हायरिंग अॅनालिटिक्स उघडा', action: 'view analytics' },
  ]},
};

const ADMIN_COMMANDS = {
  'en-US': { key: 'admin', category: '🛡️ Admin Tools', commands: [
    { text: '• "pending jobs" - Open the moderation queue', action: 'pending moderation' },
    { text: '• "view applications" - See every application on the platform', action: 'my applications' },
    { text: '• "show analytics" - Open platform-wide analytics', action: 'view analytics' },
    { text: '• "approve job 1" - Approve a pending job straight from the queue', action: 'approve_job' },
    { text: '• "reject job 1 because spam" - Reject one with a reason', action: 'reject_job' },
  ]},
  'hi-IN': { key: 'admin', category: '🛡️ एडमिन टूल्स', commands: [
    { text: '• "pending jobs" - मॉडरेशन क्यू खोलें', action: 'pending moderation' },
    { text: '• "view applications" - प्लेटफ़ॉर्म पर सभी आवेदन देखें', action: 'my applications' },
    { text: '• "show analytics" - प्लेटफ़ॉर्म-व्यापी एनालिटिक्स खोलें', action: 'view analytics' },
    { text: '• "approve job 1" - कतार से किसी नौकरी को स्वीकृत करें', action: 'approve_job' },
    { text: '• "reject job 1 because spam" - कारण के साथ अस्वीकृत करें', action: 'reject_job' },
  ]},
  'mr-IN': { key: 'admin', category: '🛡️ अॅडमिन टूल्स', commands: [
    { text: '• "pending jobs" - मॉडरेशन क्यू उघडा', action: 'pending moderation' },
    { text: '• "view applications" - प्लॅटफॉर्मवरील सर्व अर्ज पहा', action: 'my applications' },
    { text: '• "show analytics" - प्लॅटफॉर्म-व्यापी अॅनालिटिक्स उघडा', action: 'view analytics' },
    { text: '• "approve job 1" - रांगेतून एक नोकरी मंजूर करा', action: 'approve_job' },
    { text: '• "reject job 1 because spam" - कारणासह नकार द्या', action: 'reject_job' },
  ]},
};

// Universally useful, added for every role.
const ACCOUNT_COMMANDS = {
  'en-US': { key: 'account', category: '👤 Account', commands: [
    { text: '• "my profile" - Open your profile page', action: 'my profile' },
    { text: '• "log out" - Sign out of your account', action: 'logout' },
  ]},
  'hi-IN': { key: 'account', category: '👤 खाता', commands: [
    { text: '• "my profile" - अपनी प्रोफाइल खोलें', action: 'my profile' },
    { text: '• "log out" - अपने खाते से साइन आउट करें', action: 'logout' },
  ]},
  'mr-IN': { key: 'account', category: '👤 खाते', commands: [
    { text: '• "my profile" - तुमची प्रोफाइल उघडा', action: 'my profile' },
    { text: '• "log out" - तुमच्या खात्यातून साइन आउट करा', action: 'logout' },
  ]},
};

/** The help-popup sections for the given language and role: candidate-only job-search/apply/rate
 *  sections are dropped for a recruiter or admin account (those hit endpoints that account can't
 *  use), and a role-specific tools section plus the universal Account section are appended. */
const getCommandSections = (language, { isRecruiter, isAdmin } = {}) => {
  const base = ALL_COMMANDS[language] || ALL_COMMANDS['en-US'];
  const sections = (isRecruiter || isAdmin)
    ? base.filter((section) => section.key !== 'job_search' && section.key !== 'job_actions')
    : [...base];

  if (isRecruiter) sections.push(RECRUITER_COMMANDS[language] || RECRUITER_COMMANDS['en-US']);
  if (isAdmin) sections.push(ADMIN_COMMANDS[language] || ADMIN_COMMANDS['en-US']);
  sections.push(ACCOUNT_COMMANDS[language] || ACCOUNT_COMMANDS['en-US']);

  return sections;
};

// Response templates in different languages
const RESPONSES = {
  'login_required': {
    'en-US': 'Please log in first.',
    'hi-IN': 'कृपया पहले लॉग इन करें।',
    'mr-IN': 'कृपया प्रथम लॉग इन करा.'
  },
  'finding_jobs': {
    'en-US': 'Searching for jobs...',
    'hi-IN': 'नौकरियां खोज रहा हूँ...',
    'mr-IN': 'नोकऱ्या शोधत आहे...'
  },
  'searching_by_title': {
    'en-US': 'Searching for "{title}" jobs...',
    'hi-IN': '"{title}" के लिए नौकरियां खोज रहा हूँ...',
    'mr-IN': '"{title}" साठी नोकऱ्या शोधत आहे...'
  },
  'found_jobs': {
    'en-US': 'I found {count} jobs matching "{title}".',
    'hi-IN': 'मुझे "{title}" के अनुसार {count} नौकरियां मिलीं।',
    'mr-IN': 'मला "{title}" नुसार {count} नोकऱ्या सापडल्या.'
  },
  'no_jobs_found': {
    'en-US': 'No jobs found for "{title}". Try a different search term.',
    'hi-IN': '"{title}" के लिए कोई नौकरी नहीं मिली। कोई दूसरा शब्द खोजें।',
    'mr-IN': '"{title}" साठी कोणतीही नोकरी सापडली नाही. वेगळा शब्द शोधा.'
  },
  'checking_applications': {
    'en-US': 'Checking your applications...',
    'hi-IN': 'आपके आवेदन जांच रहा हूँ...',
    'mr-IN': 'तुमचे अर्ज तपासत आहे...'
  },
  'no_applications': {
    'en-US': "You haven't applied to any jobs yet.",
    'hi-IN': 'आपने अभी तक किसी नौकरी के लिए आवेदन नहीं किया है।',
    'mr-IN': 'तुम्ही अद्याप कोणत्याही नोकरीसाठी अर्ज केलेला नाही.'
  },
  'error_finding_jobs': {
    'en-US': 'Sorry, I had trouble finding jobs.',
    'hi-IN': 'क्षमा करें, मुझे नौकरियां ढूंढने में समस्या हुई।',
    'mr-IN': 'क्षमस्व, मला नोकऱ्या शोधताना अडचण आली.'
  },
  // Was previously missing - fetchApplications()'s catch block referenced this key, but with no
  // entry here getResponse() fell back to returning the literal key string as the spoken/shown
  // message instead of a real sentence.
  'error_fetching_applications': {
    'en-US': "Sorry, I had trouble checking your applications.",
    'hi-IN': 'क्षमा करें, आपके आवेदन जांचने में समस्या हुई।',
    'mr-IN': 'क्षमस्व, तुमचे अर्ज तपासताना अडचण आली.'
  },
  'opening_profile': {
    'en-US': 'Opening your profile.',
    'hi-IN': 'आपकी प्रोफाइल खोल रहा हूँ।',
    'mr-IN': 'तुमची प्रोफाइल उघडत आहे.'
  },
  'logging_out': {
    'en-US': 'Logging you out. See you next time!',
    'hi-IN': 'लॉग आउट कर रहा हूँ। फिर मिलेंगे!',
    'mr-IN': 'लॉग आउट करत आहे. पुन्हा भेटू!'
  },
  'opening_recruiter_dashboard': {
    'en-US': 'Opening your recruiter dashboard.',
    'hi-IN': 'आपका रिक्रूटर डैशबोर्ड खोल रहा हूँ।',
    'mr-IN': 'तुमचा रिक्रूटर डॅशबोर्ड उघडत आहे.'
  },
  'opening_discover': {
    'en-US': 'Opening Discover Candidates.',
    'hi-IN': 'उम्मीदवार खोज खोल रहा हूँ।',
    'mr-IN': 'उमेदवार शोध उघडत आहे.'
  },
  'opening_post_job': {
    'en-US': "Opening your jobs tab - click 'Post New Job' to create one.",
    'hi-IN': "आपका जॉब्स टैब खोल रहा हूँ - नई नौकरी बनाने के लिए 'Post New Job' पर क्लिक करें।",
    'mr-IN': "तुमचे जॉब्स टॅब उघडत आहे - नवीन नोकरी तयार करण्यासाठी 'Post New Job' वर क्लिक करा."
  },
  'opening_analytics': {
    'en-US': 'Opening analytics.',
    'hi-IN': 'एनालिटिक्स खोल रहा हूँ।',
    'mr-IN': 'अॅनालिटिक्स उघडत आहे.'
  },
  'opening_admin_dashboard': {
    'en-US': 'Opening the admin dashboard.',
    'hi-IN': 'एडमिन डैशबोर्ड खोल रहा हूँ।',
    'mr-IN': 'अॅडमिन डॅशबोर्ड उघडत आहे.'
  },
  'opening_pending_moderation': {
    'en-US': 'Opening the pending jobs moderation queue.',
    'hi-IN': 'लंबित नौकरी मॉडरेशन क्यू खोल रहा हूँ।',
    'mr-IN': 'प्रलंबित नोकरी मॉडरेशन क्यू उघडत आहे.'
  },
  'recruiter_applications_hint': {
    'en-US': "Opening your dashboard - click 'View Applications' on a job to see its applicants.",
    'hi-IN': "आपका डैशबोर्ड खोल रहा हूँ - आवेदकों को देखने के लिए किसी नौकरी पर 'View Applications' पर क्लिक करें।",
    'mr-IN': "तुमचा डॅशबोर्ड उघडत आहे - अर्जदार पाहण्यासाठी एखाद्या नोकरीवर 'View Applications' वर क्लिक करा."
  },
  'admin_applications_hint': {
    'en-US': 'Opening all applications.',
    'hi-IN': 'सभी आवेदन खोल रहा हूँ।',
    'mr-IN': 'सर्व अर्ज उघडत आहे.'
  },
  'no_pending_job_found': {
    'en-US': "I couldn't tell which pending job you mean. Try \"pending jobs\" first to see the queue, then say \"approve job 1\".",
    'hi-IN': 'मुझे समझ नहीं आया कौन सी लंबित नौकरी। पहले "pending jobs" कहें, फिर "approve job 1" कहें।',
    'mr-IN': 'मला कोणती प्रलंबित नोकरी हे कळले नाही. आधी "pending jobs" म्हणा, मग "approve job 1" म्हणा.'
  },
  'error_moderation': {
    'en-US': "Sorry, that moderation action didn't go through.",
    'hi-IN': 'क्षमा करें, वह मॉडरेशन कार्रवाई नहीं हो सकी।',
    'mr-IN': 'क्षमस्व, ती मॉडरेशन कृती होऊ शकली नाही.'
  },
  'nothing_to_repeat': {
    'en-US': "I haven't said anything yet.",
    'hi-IN': 'मैंने अभी तक कुछ नहीं कहा है।',
    'mr-IN': 'मी अजून काही बोललो नाही.'
  },
  'stopped_speaking': {
    'en-US': 'Stopped.',
    'hi-IN': 'रुक गया।',
    'mr-IN': 'थांबले.'
  },
  'opening_dashboard': {
    'en-US': 'Opening dashboard.',
    'hi-IN': 'डैशबोर्ड खोल रहा हूँ।',
    'mr-IN': 'डॅशबोर्ड उघडत आहे.'
  },
  'opening_upload': {
    'en-US': 'Opening resume upload page.',
    'hi-IN': 'रिज्यूमे अपलोड पेज खोल रहा हूँ।',
    'mr-IN': 'रिझ्यूमे अपलोड पेज उघडत आहे.'
  },
  'opening_jobs': {
    'en-US': 'Opening jobs page.',
    'hi-IN': 'नौकरियों का पेज खोल रहा हूँ।',
    'mr-IN': 'नोकऱ्यांचे पेज उघडत आहे.'
  },
  'opening_home': {
    'en-US': 'Going to home page.',
    'hi-IN': 'होम पेज पर जा रहा हूँ।',
    'mr-IN': 'होम पेजवर जात आहे.'
  },
  'not_supported': {
    'en-US': 'Speech recognition not supported in your browser.',
    'hi-IN': 'आपके ब्राउज़र में स्पीच रिकग्निशन समर्थित नहीं है।',
    'mr-IN': 'तुमच्या ब्राउझरमध्ये स्पीच रिकग्निशन समर्थित नाही.'
  },
  'microphone_required': {
    'en-US': 'Please allow microphone access.',
    'hi-IN': 'कृपया माइक्रोफोन एक्सेस दें।',
    'mr-IN': 'कृपया मायक्रोफोन प्रवेश द्या.'
  },
  'unknown_command': {
    'en-US': "I didn't understand. Try saying 'help' to see all commands.",
    'hi-IN': "मैं समझ नहीं पाया। सभी कमांड देखने के लिए 'मदद' कहें।",
    'mr-IN': "मला समजले नाही. सर्व कमांड पाहण्यासाठी 'मदत' म्हणा."
  },
  'approving_job': {
    'en-US': 'Approving "{title}"...',
    'hi-IN': '"{title}" को स्वीकृत कर रहा हूँ...',
    'mr-IN': '"{title}" मंजूर करत आहे...'
  },
  'job_approved': {
    'en-US': '"{title}" has been approved and is now live.',
    'hi-IN': '"{title}" स्वीकृत हो गई है और अब लाइव है।',
    'mr-IN': '"{title}" मंजूर झाली आहे आणि आता लाइव्ह आहे.'
  },
  'rejecting_job': {
    'en-US': 'Rejecting "{title}"...',
    'hi-IN': '"{title}" को अस्वीकृत कर रहा हूँ...',
    'mr-IN': '"{title}" नकारत आहे...'
  },
  'job_rejected': {
    'en-US': '"{title}" has been rejected.',
    'hi-IN': '"{title}" अस्वीकृत कर दी गई है।',
    'mr-IN': '"{title}" नकारली गेली आहे.'
  }
};

const VoiceAssistant = ({ onAuthChange }) => {
  const [isListening, setIsListening] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [speechEnabled, setSpeechEnabled] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);
  const [currentLanguage, setCurrentLanguage] = useState('en-US');
  const [availableVoices, setAvailableVoices] = useState([]);
  const [showHelpPopup, setShowHelpPopup] = useState(false);
  const [searchResults, setSearchResults] = useState([]);
  const [showSearchResults, setShowSearchResults] = useState(false);
  const [allJobs, setAllJobs] = useState([]);
  const [pendingJobs, setPendingJobs] = useState([]);
  const [playStartSound, setPlayStartSound] = useState(false);
  const [playStopSound, setPlayStopSound] = useState(false);
  
  // Application modal state
  const [showApplicationModal, setShowApplicationModal] = useState(false);
  const [selectedJobForApplication, setSelectedJobForApplication] = useState(null);
  
  const recognitionRef = useRef(null);
  const synthesisRef = useRef(null);
  const mediaStreamRef = useRef(null);
  const audioContextRef = useRef(null);
  const navigate = useNavigate();
  const messagesEndRef = useRef(null);
  const textInputRef = useRef(null);

  const currentUser = JSON.parse(localStorage.getItem('user'));
  const userRole = currentUser?.role; // 'ROLE_USER' | 'ROLE_RECRUITER' | 'ROLE_ADMIN'
  const isRecruiter = userRole === 'ROLE_RECRUITER';
  const isAdmin = userRole === 'ROLE_ADMIN';
  const [textInput, setTextInput] = useState('');

  // Load available voices
  useEffect(() => {
    const loadVoices = () => {
      const voices = window.speechSynthesis.getVoices();
      setAvailableVoices(voices);
    };

    loadVoices();
    if (window.speechSynthesis.onvoiceschanged !== undefined) {
      window.speechSynthesis.onvoiceschanged = loadVoices;
    }
  }, []);

  // Welcome message - what it suggests trying first depends on who's logged in.
  useEffect(() => {
    const welcomeMessages = isRecruiter ? {
      'en-US': "Hello! I'm your DreamRole assistant. Say 'post a job', 'discover candidates', or 'show analytics' - or 'help' to see everything I can do.",
      'hi-IN': "नमस्ते! मैं आपका DreamRole सहायक हूँ। 'post a job', 'discover candidates', या 'show analytics' कहें - या सभी कमांड देखने के लिए 'help' कहें।",
      'mr-IN': "नमस्कार! मी तुमचा DreamRole सहाय्यक आहे. 'post a job', 'discover candidates', किंवा 'show analytics' म्हणा - किंवा सर्व कमांड पाहण्यासाठी 'help' म्हणा."
    } : isAdmin ? {
      'en-US': "Hello! I'm your DreamRole assistant. Say 'pending jobs' to open the moderation queue, or 'approve job 1' / 'reject job 1 because spam' to act on one directly. Say 'help' to see everything I can do.",
      'hi-IN': "नमस्ते! मैं आपका DreamRole सहायक हूँ। मॉडरेशन क्यू खोलने के लिए 'pending jobs' कहें, या सीधे कार्रवाई के लिए 'approve job 1' कहें। सभी कमांड देखने के लिए 'help' कहें।",
      'mr-IN': "नमस्कार! मी तुमचा DreamRole सहाय्यक आहे. मॉडरेशन क्यू उघडण्यासाठी 'pending jobs' म्हणा, किंवा थेट कृतीसाठी 'approve job 1' म्हणा. सर्व कमांड पाहण्यासाठी 'help' म्हणा."
    } : {
      'en-US': "Hello! I'm your DreamRole assistant. Say 'recommend jobs' or 'browse jobs' to go to jobs page, or search for specific jobs like 'find data scientist jobs'.",
      'hi-IN': "नमस्ते! मैं आपका DreamRole सहायक हूँ। जॉब पेज पर जाने के लिए 'recommend jobs' या 'browse jobs' कहें, या विशिष्ट नौकरियां खोजें जैसे 'डाटा साइंटिस्ट नौकरी'।",
      'mr-IN': "नमस्कार! मी तुमचा DreamRole सहाय्यक आहे. जॉब पेजवर जाण्यासाठी 'recommend jobs' किंवा 'browse jobs' म्हणा, किंवा विशिष्ट नोकऱ्या शोधा जसे 'डेटा सायंटिस्ट नोकरी'."
    };

    setMessages([{
      type: 'assistant',
      text: welcomeMessages[currentLanguage],
      language: currentLanguage
    }]);
    
    // Fetch all jobs on load (only once a user is logged in; the API requires auth)
    if (currentUser) {
      fetchAllJobs();
      // An admin also gets the pending-moderation queue preloaded, so "approve job 1" works
      // immediately without having to say "pending jobs" first.
      if (isAdmin) fetchPendingJobs();
    }
  }, []);

  // Alt+A opens the assistant and focuses the type-a-command box from anywhere on the page - a
  // keyboard-only path in that needs neither the mouse nor microphone permission. Guarded against
  // ctrlKey/metaKey so it doesn't fire for AltGr-based characters on non-US keyboard layouts
  // (Windows reports AltGr as ctrlKey+altKey together).
  useEffect(() => {
    const handleGlobalShortcut = (e) => {
      if (e.altKey && !e.ctrlKey && !e.metaKey && e.key.toLowerCase() === 'a') {
        e.preventDefault();
        setIsOpen(true);
      }
    };
    window.addEventListener('keydown', handleGlobalShortcut);
    return () => window.removeEventListener('keydown', handleGlobalShortcut);
  }, []);

  useEffect(() => {
    if (isOpen) {
      textInputRef.current?.focus();
    }
  }, [isOpen]);

  // Play sound effects
  useEffect(() => {
    if (playStartSound) {
      playBeepSound(700, 100); // Higher pitch for start
      setPlayStartSound(false);
    }
    if (playStopSound) {
      playBeepSound(400, 80); // Lower pitch for stop
      setPlayStopSound(false);
    }
  }, [playStartSound, playStopSound]);

  // Simple beep sound using Web Audio API
  const playBeepSound = (frequency, duration) => {
    try {
      if (!audioContextRef.current) {
        audioContextRef.current = new (window.AudioContext || window.webkitAudioContext)();
      }
      
      const context = audioContextRef.current;
      
      // Resume context if suspended
      if (context.state === 'suspended') {
        context.resume();
      }
      
      const oscillator = context.createOscillator();
      const gainNode = context.createGain();
      
      oscillator.connect(gainNode);
      gainNode.connect(context.destination);
      
      oscillator.frequency.value = frequency;
      oscillator.type = 'sine';
      
      gainNode.gain.setValueAtTime(0.1, context.currentTime);
      gainNode.gain.exponentialRampToValueAtTime(0.01, context.currentTime + duration / 1000);
      
      oscillator.start();
      oscillator.stop(context.currentTime + duration / 1000);
    } catch (error) {
    }
  };

  // Scroll to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, searchResults]);

  // Initialize speech recognition
  useEffect(() => {
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      recognitionRef.current = new SpeechRecognition();
      
      const recognition = recognitionRef.current;
      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.lang = currentLanguage;

      recognition.onstart = () => {
        setIsListening(true);
        setPlayStartSound(true); // Play start sound
      };
      
      recognition.onend = () => {
        setIsListening(false);
        setIsProcessing(false);
        setTranscript('');
        stopMicrophone();
      };

      recognition.onresult = (event) => {
        const finalTranscript = Array.from(event.results)
          .map(result => result[0].transcript)
          .join('');
        
        setTranscript(finalTranscript);
        if (event.results[0].isFinal) {
          handleVoiceCommand(finalTranscript.trim(), currentLanguage);
        }
      };

      recognition.onerror = (event) => {
        console.error('Speech error:', event.error);
        setIsListening(false);
        setIsProcessing(false);
        stopMicrophone();
        if (event.error === 'not-allowed') {
          addMessage('assistant', getResponse('microphone_required', currentLanguage), currentLanguage);
        }
      };
    }

    return () => {
      stopAllAudio();
      if (audioContextRef.current) {
        audioContextRef.current.close();
      }
    };
  }, [currentLanguage]);

  // Helper function to stop microphone
  const stopMicrophone = () => {
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach(track => {
        track.stop();
      });
      mediaStreamRef.current = null;
    }
  };

  // Helper function to stop all audio
  const stopAllAudio = () => {
    if (synthesisRef.current) {
      window.speechSynthesis.cancel();
      setIsSpeaking(false);
      synthesisRef.current = null;
    }
    
    if (recognitionRef.current) {
      try {
        recognitionRef.current.stop();
      } catch (e) {
      }
    }
    
    stopMicrophone();
    setIsListening(false);
  };

  const addMessage = (type, text, language = currentLanguage, data = null) => {
    setMessages(prev => [...prev, { type, text, language, data }]);
  };

  const getResponse = (key, language, params = {}) => {
    let template = RESPONSES[key]?.[language] || RESPONSES[key]?.['en-US'] || key;
    
    // Replace parameters in template
    Object.keys(params).forEach(param => {
      template = template.replace(`{${param}}`, params[param]);
    });
    
    return template;
  };

  // Get voice for current language
  const getVoiceForLanguage = (langCode) => {
    if (!availableVoices.length) return null;
    
    const langConfig = LANGUAGES[langCode];
    
    for (const voiceName of langConfig.voiceNames) {
      const voice = availableVoices.find(v => v.name.includes(voiceName));
      if (voice) return voice;
    }
    
    return availableVoices.find(v => v.lang.startsWith(langCode.split('-')[0]));
  };

  const speakResponse = (text, language = currentLanguage) => {
    if (!speechEnabled || !('speechSynthesis' in window)) return;

    // Cancel any ongoing speech before starting new one
    window.speechSynthesis.cancel();
    
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = language;
    const voice = getVoiceForLanguage(language);
    if (voice) utterance.voice = voice;
    
    utterance.rate = 0.9;
    utterance.onstart = () => setIsSpeaking(true);
    utterance.onend = () => setIsSpeaking(false);
    utterance.onerror = () => setIsSpeaking(false);
    
    window.speechSynthesis.speak(utterance);
    synthesisRef.current = utterance;
  };

  // Show help popup
  const showHelpCommands = () => {
    setShowHelpPopup(true);
  };

  // Open application form
  const openApplicationForm = (job) => {
    if (!currentUser) {
      const msg = getResponse('login_required', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
      return;
    }
    
    setSelectedJobForApplication(job);
    setShowApplicationModal(true);
    setIsOpen(false); // Close assistant while form is open
  };

  // Rate a job by voice: the rating is already a single spoken value, so this submits
  // directly instead of opening the ratings form (which is for adding a written comment).
  const rateJobByVoice = async (job, rating) => {
    if (!currentUser) {
      const msg = getResponse('login_required', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
      return;
    }

    try {
      await api.post(`/jobs/${job.id}/rate/${currentUser.id}`, { rating, comment: '' });
      const templates = {
        'en-US': `Rated "${job.title}" ${rating} star${rating > 1 ? 's' : ''}. Thanks for the feedback!`,
        'hi-IN': `"${job.title}" को ${rating} स्टार दिया गया। प्रतिक्रिया के लिए धन्यवाद!`,
        'mr-IN': `"${job.title}" ला ${rating} स्टार दिले. अभिप्रायाबद्दल धन्यवाद!`,
      };
      const msg = templates[currentLanguage] || templates['en-US'];
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
    } catch (error) {
      const templates = {
        'en-US': "Sorry, I couldn't submit that rating.",
        'hi-IN': 'माफ़ करें, रेटिंग सबमिट नहीं हो सकी।',
        'mr-IN': 'माफ करा, रेटिंग सबमिट करता आली नाही.',
      };
      const msg = templates[currentLanguage] || templates['en-US'];
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
    }
  };

  // Handle application success
  const handleApplicationSuccess = () => {
    const successMessages = {
      'en-US': '✅ Application submitted successfully!',
      'hi-IN': '✅ आवेदन सफलतापूर्वक जमा हुआ!',
      'mr-IN': '✅ अर्ज यशस्वीरित्या सबमिट झाला!'
    };
    
    addMessage('assistant', successMessages[currentLanguage] || successMessages['en-US'], currentLanguage);
    speakResponse(successMessages[currentLanguage] || successMessages['en-US'], currentLanguage);
    
    setShowApplicationModal(false);
    setSelectedJobForApplication(null);
    setTimeout(() => setIsOpen(true), 500);
  };

  // Handle application modal close
  const handleApplicationClose = () => {
    setShowApplicationModal(false);
    setSelectedJobForApplication(null);
    setTimeout(() => setIsOpen(true), 500);
  };

  // Extract job title from command
  const extractJobTitle = (command) => {
    const lowerCommand = command.toLowerCase();
    
    // Common job titles to look for
    const commonJobTitles = [
      'software developer', 'software engineer', 'frontend developer', 'backend developer',
      'full stack developer', 'data scientist', 'data analyst', 'data engineer',
      'devops engineer', 'cloud engineer', 'system administrator',
      'project manager', 'product manager', 'scrum master',
      'business analyst', 'marketing manager', 'sales manager',
      'ux designer', 'ui designer', 'graphic designer',
      'network engineer', 'security engineer', 'database administrator',
      'java developer', 'python developer', 'react developer',
      'angular developer', 'node.js developer', 'spring boot developer',
      'machine learning engineer', 'ai engineer', 'research scientist',
      'technical lead', 'architect', 'consultant',
      'senior developer', 'junior developer', 'lead developer',
      'test engineer', 'qa engineer', 'automation engineer'
    ];
    
    // First check if the command contains any common job title
    for (const title of commonJobTitles) {
      if (lowerCommand.includes(title)) {
        return title;
      }
    }
    
    // If not found in common titles, try to extract using patterns
    const patterns = [
      /(?:search for|find|looking for|show me|i want)\s+([a-z\s]+?)(?:\s+jobs?|\s+positions?|\s+roles?|$)/i,
      /([a-z\s]+?)\s+(?:jobs?|positions?|roles?|openings?)/i,
      /(?:as a|as an)\s+([a-z\s]+)/i
    ];
    
    for (const pattern of patterns) {
      const match = lowerCommand.match(pattern);
      if (match && match[1]) {
        return match[1].trim();
      }
    }
    
    // If still not found, check if the command contains any job-related keywords
    const jobKeywords = [
      'developer', 'engineer', 'scientist', 'analyst', 'manager',
      'designer', 'architect', 'consultant', 'specialist',
      'administrator', 'coordinator', 'associate'
    ];
    
    for (const keyword of jobKeywords) {
      if (lowerCommand.includes(keyword)) {
        // Extract the word containing the keyword
        const words = lowerCommand.split(' ');
        for (const word of words) {
          if (word.includes(keyword)) {
            return word;
          }
        }
      }
    }
    
    return null;
  };

  // Fetch all jobs
  const fetchAllJobs = async () => {
    try {
      const response = await api.get('/jobs');
      const jobs = response.data || [];
      setAllJobs(jobs);
      return jobs;
    } catch (error) {
      console.error('Error fetching jobs:', error);
      return [];
    }
  };

  // Admin-only: the moderation queue "approve job N" / "reject job N" resolve against.
  const fetchPendingJobs = async () => {
    try {
      const response = await api.get('/admin/jobs/pending');
      const jobs = response.data || [];
      setPendingJobs(jobs);
      return jobs;
    } catch (error) {
      console.error('Error fetching pending jobs:', error);
      return [];
    }
  };

  // Approve/reject a pending job by voice - the same two actions AdminDashboard's bulk-moderation
  // buttons call, just resolved from a spoken job reference instead of a checkbox selection.
  const approveJobByVoice = async (job) => {
    const startMsg = getResponse('approving_job', currentLanguage, { title: job.title });
    addMessage('assistant', startMsg, currentLanguage);
    speakResponse(startMsg, currentLanguage);

    try {
      await api.put(`/admin/jobs/${job.id}/approve`);
      setPendingJobs(prev => prev.filter(j => j.id !== job.id));
      const doneMsg = getResponse('job_approved', currentLanguage, { title: job.title });
      addMessage('assistant', doneMsg, currentLanguage);
      speakResponse(doneMsg, currentLanguage);
    } catch (error) {
      const errMsg = getResponse('error_moderation', currentLanguage);
      addMessage('assistant', errMsg, currentLanguage);
      speakResponse(errMsg, currentLanguage);
    }
  };

  const rejectJobByVoice = async (job, reason) => {
    const startMsg = getResponse('rejecting_job', currentLanguage, { title: job.title });
    addMessage('assistant', startMsg, currentLanguage);
    speakResponse(startMsg, currentLanguage);

    try {
      await api.put(`/admin/jobs/${job.id}/reject`, { reason });
      setPendingJobs(prev => prev.filter(j => j.id !== job.id));
      const doneMsg = getResponse('job_rejected', currentLanguage, { title: job.title });
      addMessage('assistant', doneMsg, currentLanguage);
      speakResponse(doneMsg, currentLanguage);
    } catch (error) {
      const errMsg = getResponse('error_moderation', currentLanguage);
      addMessage('assistant', errMsg, currentLanguage);
      speakResponse(errMsg, currentLanguage);
    }
  };

  // The reason clause of a spoken rejection, e.g. "reject job 1 because it looks like spam" ->
  // "it looks like spam". Falls back to a generic reason when none was given, since the backend
  // reject endpoint expects one.
  const extractRejectReason = (command) => {
    const match = command.match(/(?:because|reason(?:\s+is)?)\s*[:\-]?\s*(.+)$/i);
    return match ? match[1].trim() : null;
  };

  // Search jobs by title
  const searchJobsByTitle = async (searchTerm) => {
    if (!currentUser) {
      const msg = getResponse('login_required', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
      return;
    }

    setIsProcessing(true);
    
    const searchingMsg = getResponse('searching_by_title', currentLanguage, { title: searchTerm });
    addMessage('assistant', searchingMsg, currentLanguage);
    speakResponse(searchingMsg, currentLanguage);

    try {
      // Use allJobs if already loaded, otherwise fetch
      const jobs = allJobs.length > 0 ? allJobs : await fetchAllJobs();
      
      // Log for debugging
      
      // Better matching algorithm
      const searchLower = searchTerm.toLowerCase().trim();
      const searchWords = searchLower.split(' ').filter(w => w.length > 2);
      
      const filteredJobs = jobs.filter(job => {
        const title = (job.title || '').toLowerCase();
        const description = (job.description || '').toLowerCase();
        const company = (job.company || '').toLowerCase();
        
        // Check for exact match first
        if (title.includes(searchLower)) {
          return true;
        }
        
        // Check for word-by-word match
        for (const word of searchWords) {
          if (title.includes(word)) {
            return true;
          }
        }
        
        // Check description for keywords
        for (const word of searchWords) {
          if (description.includes(word) || company.includes(word)) {
            return true;
          }
        }
        
        return false;
      });
      
      
      setSearchResults(filteredJobs);
      setShowSearchResults(true);
      
      if (filteredJobs.length > 0) {
        const foundMsg = getResponse('found_jobs', currentLanguage, { 
          count: filteredJobs.length, 
          title: searchTerm 
        });
        addMessage('assistant', foundMsg, currentLanguage, { jobs: filteredJobs.slice(0, 5) });
        speakResponse(foundMsg, currentLanguage);
      } else {
        const noJobsMsg = getResponse('no_jobs_found', currentLanguage, { title: searchTerm });
        addMessage('assistant', noJobsMsg, currentLanguage);
        speakResponse(noJobsMsg, currentLanguage);
        
        // Show all jobs as suggestion
        if (jobs.length > 0) {
          addMessage('assistant', 'Here are all available jobs you can browse:', currentLanguage, { jobs: jobs.slice(0, 3) });
        }
      }
    } catch (error) {
      console.error('Error searching jobs:', error);
      addMessage('assistant', getResponse('error_finding_jobs', currentLanguage), currentLanguage);
      speakResponse(getResponse('error_finding_jobs', currentLanguage), currentLanguage);
    } finally {
      setIsProcessing(false);
    }
  };

  // Fetch applications
  const fetchApplications = async () => {
    if (!currentUser) {
      const msg = getResponse('login_required', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
      return;
    }

    setIsProcessing(true);
    addMessage('assistant', getResponse('checking_applications', currentLanguage), currentLanguage);
    speakResponse(getResponse('checking_applications', currentLanguage), currentLanguage);

    try {
      const response = await api.get(`/applications/user/${currentUser.id}`);
      const applications = response.data.applications || [];

      if (applications.length === 0) {
        addMessage('assistant', getResponse('no_applications', currentLanguage), currentLanguage);
        speakResponse(getResponse('no_applications', currentLanguage), currentLanguage);
      } else {
        const pending = applications.filter(a => a.status === 'APPLICATION_SUBMITTED').length;
        const shortlisted = applications.filter(a => a.status === 'SHORTLISTED').length;
        
        const statusTemplates = {
          'en-US': `You have ${applications.length} applications: ${pending} pending, ${shortlisted} shortlisted.`,
          'hi-IN': `आपके पास ${applications.length} आवेदन हैं: ${pending} लंबित, ${shortlisted} शॉर्टलिस्टेड।`,
          'mr-IN': `तुमच्याकडे ${applications.length} अर्ज आहेत: ${pending} प्रलंबित, ${shortlisted} शॉर्टलिस्टेड.`
        };
        
        const statusText = statusTemplates[currentLanguage] || statusTemplates['en-US'];
        addMessage('assistant', statusText, currentLanguage, { applications: applications.slice(0, 3) });
        speakResponse(statusText, currentLanguage);
      }
    } catch (error) {
      console.error('Error:', error);
      addMessage('assistant', getResponse('error_fetching_applications', currentLanguage), currentLanguage);
      speakResponse(getResponse('error_fetching_applications', currentLanguage), currentLanguage);
    } finally {
      setIsProcessing(false);
    }
  };

  // "Dashboard" means something different depending on who's asking - route to each role's own
  // dashboard instead of always the candidate one.
  const goToDashboard = () => {
    if (isAdmin) {
      navigate('/admin/dashboard');
      const msg = getResponse('opening_admin_dashboard', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
    } else {
      // /dashboard itself already renders RecruiterDashboard vs the candidate Dashboard
      // depending on role (see App.jsx), so this one route covers both.
      navigate('/dashboard');
      const key = isRecruiter ? 'opening_recruiter_dashboard' : 'opening_dashboard';
      const msg = getResponse(key, currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
    }
  };

  // Likewise, "my applications" means "applications I submitted" for a candidate, but "the
  // applicants on my jobs" for a recruiter and "every application on the platform" for an admin -
  // recruiter/admin dashboards don't have a single aggregate list to fetch, so these just land on
  // the right screen with a spoken pointer to where the data actually lives.
  const goToApplications = () => {
    if (isRecruiter) {
      navigate('/recruiter/dashboard', { state: { initialTab: 'jobs' } });
      const msg = getResponse('recruiter_applications_hint', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
    } else if (isAdmin) {
      navigate('/admin/dashboard', { state: { initialTab: 'applications' } });
      const msg = getResponse('admin_applications_hint', currentLanguage);
      addMessage('assistant', msg, currentLanguage);
      speakResponse(msg, currentLanguage);
    } else {
      fetchApplications();
    }
  };

  const goToAnalytics = () => {
    if (isAdmin) {
      navigate('/admin/dashboard', { state: { initialTab: 'analytics' } });
    } else {
      navigate('/recruiter/dashboard', { state: { initialTab: 'analytics' } });
    }
    const msg = getResponse('opening_analytics', currentLanguage);
    addMessage('assistant', msg, currentLanguage);
    speakResponse(msg, currentLanguage);
  };

  const logOutByVoice = () => {
    const msg = getResponse('logging_out', currentLanguage);
    addMessage('assistant', msg, currentLanguage);
    speakResponse(msg, currentLanguage);
    authService.logout();
    if (onAuthChange) onAuthChange(null);
    setTimeout(() => navigate('/'), 300);
  };

  // Execute command from click
  const executeCommand = (command) => {
    setShowHelpPopup(false);
    
    switch(command) {
      case 'recommend jobs':
      case 'browse jobs':
        navigate('/jobs');
        addMessage('assistant', 'Opening jobs page.', currentLanguage);
        speakResponse('Opening jobs page.', currentLanguage);
        break;
      case 'search jobs':
        addMessage('assistant', 'Please say what you want to search for, like "find data scientist jobs"', currentLanguage);
        speakResponse('Please say what you want to search for', currentLanguage);
        break;
      case 'search_by_title':
        addMessage('assistant', 'Please say the job title you want to search for, like "find data scientist jobs"', currentLanguage);
        speakResponse('Please say the job title you want to search for', currentLanguage);
        break;
      case 'apply_to_job':
        addMessage('assistant', 'Search for jobs first, then say something like "apply to job 1".', currentLanguage);
        speakResponse('Search for jobs first, then say apply to job 1', currentLanguage);
        break;
      case 'rate_job':
        addMessage('assistant', 'Search for jobs first, then say something like "rate job 1 five stars".', currentLanguage);
        speakResponse('Search for jobs first, then say rate job 1 five stars', currentLanguage);
        break;
      case 'approve_job':
        addMessage('assistant', 'Say "pending jobs" first to see the queue, then say something like "approve job 1".', currentLanguage);
        speakResponse('Say pending jobs first, then say approve job 1', currentLanguage);
        break;
      case 'reject_job':
        addMessage('assistant', 'Say "pending jobs" first to see the queue, then say something like "reject job 1 because it\'s spam".', currentLanguage);
        speakResponse('Say pending jobs first, then say reject job 1 with a reason', currentLanguage);
        break;
      case 'my applications':
        goToApplications();
        break;
      case 'go to dashboard':
        goToDashboard();
        break;
      case 'my profile':
        navigate('/profile');
        addMessage('assistant', getResponse('opening_profile', currentLanguage), currentLanguage);
        speakResponse(getResponse('opening_profile', currentLanguage), currentLanguage);
        break;
      case 'logout':
        logOutByVoice();
        break;
      case 'post job':
        navigate('/recruiter/dashboard', { state: { initialTab: 'jobs' } });
        addMessage('assistant', getResponse('opening_post_job', currentLanguage), currentLanguage);
        speakResponse(getResponse('opening_post_job', currentLanguage), currentLanguage);
        break;
      case 'discover candidates':
        navigate('/recruiter/dashboard', { state: { initialTab: 'discover' } });
        addMessage('assistant', getResponse('opening_discover', currentLanguage), currentLanguage);
        speakResponse(getResponse('opening_discover', currentLanguage), currentLanguage);
        break;
      case 'view analytics':
        goToAnalytics();
        break;
      case 'pending moderation':
        navigate('/admin/dashboard', { state: { initialTab: 'pending-jobs' } });
        fetchPendingJobs(); // refresh in case the queue changed since mount
        addMessage('assistant', getResponse('opening_pending_moderation', currentLanguage), currentLanguage);
        speakResponse(getResponse('opening_pending_moderation', currentLanguage), currentLanguage);
        break;
      case 'upload resume':
        navigate('/upload');
        addMessage('assistant', getResponse('opening_upload', currentLanguage), currentLanguage);
        speakResponse(getResponse('opening_upload', currentLanguage), currentLanguage);
        break;
      case 'go to home':
        navigate('/');
        addMessage('assistant', getResponse('opening_home', currentLanguage), currentLanguage);
        speakResponse(getResponse('opening_home', currentLanguage), currentLanguage);
        break;
      case 'help':
        showHelpCommands();
        break;
      case 'hello':
        const greetingTemplates = {
          'en-US': currentUser ? `Hello ${currentUser.fullName}! How can I help?` : "Hello! Please log in to use all features.",
          'hi-IN': currentUser ? `नमस्ते ${currentUser.fullName}! मैं आपकी कैसे मदद कर सकता हूँ?` : "नमस्ते! सभी सुविधाओं का उपयोग करने के लिए कृपया लॉग इन करें।",
          'mr-IN': currentUser ? `नमस्कार ${currentUser.fullName}! मी तुम्हाला कशी मदत करू शकतो?` : "नमस्कार! सर्व सुविधा वापरण्यासाठी कृपया लॉग इन करा."
        };
        addMessage('assistant', greetingTemplates[currentLanguage] || greetingTemplates['en-US'], currentLanguage);
        speakResponse(greetingTemplates[currentLanguage] || greetingTemplates['en-US'], currentLanguage);
        break;
      default:
        break;
    }
  };

  // Detect command from user input
  const detectCommand = (userInput, language) => {
    const lowerInput = userInput.toLowerCase();
    
    for (const [command, translations] of Object.entries(COMMAND_MAPPINGS)) {
      const phrases = translations[language] || [];
      for (const phrase of phrases) {
        if (lowerInput.includes(phrase.toLowerCase())) {
          return command;
        }
      }
    }
    
    return null;
  };

  // Handle voice commands
  const handleVoiceCommand = (command, language) => {
    addMessage('user', command, language);
    
    const detectedCommand = detectCommand(command, language);
    const lowerCommand = command.toLowerCase();

    // Help command
    if (detectedCommand === 'help' || 
        lowerCommand.includes('help') ||
        lowerCommand.includes('मदद') ||
        lowerCommand.includes('मदत')) {
      showHelpCommands();
      const helpMessages = {
        'en-US': 'Showing all available commands.',
        'hi-IN': 'सभी उपलब्ध कमांड दिखा रहा हूँ।',
        'mr-IN': 'सर्व उपलब्ध कमांड दाखवत आहे.'
      };
      addMessage('assistant', helpMessages[language] || helpMessages['en-US'], language);
      speakResponse(helpMessages[language] || helpMessages['en-US'], language);
    }
    
    // RECOMMEND JOBS - Direct navigation to jobs page
    else if (detectedCommand === 'recommend jobs') {
      navigate('/jobs');
      const msg = {
        'en-US': 'Opening jobs page with recommendations.',
        'hi-IN': 'अनुशंसाओं के साथ जॉब पेज खोल रहा हूँ।',
        'mr-IN': 'शिफारशींसह जॉब पेज उघडत आहे.'
      };
      addMessage('assistant', msg[language] || msg['en-US'], language);
      speakResponse(msg[language] || msg['en-US'], language);
    }
    
    // BROWSE JOBS - Direct navigation to jobs page
    else if (detectedCommand === 'browse jobs') {
      navigate('/jobs');
      const msg = {
        'en-US': 'Opening jobs page.',
        'hi-IN': 'जॉब पेज खोल रहा हूँ।',
        'mr-IN': 'जॉब पेज उघडत आहे.'
      };
      addMessage('assistant', msg[language] || msg['en-US'], language);
      speakResponse(msg[language] || msg['en-US'], language);
    }
    
    // APPLY TO A JOB - e.g. "apply to job 2", "apply to the first job", "apply to data scientist"
    else if (lowerCommand.includes('apply') || lowerCommand.includes('अप्लाई') || lowerCommand.includes('अप्लाय')) {
      const candidateJobs = searchResults.length > 0 ? searchResults : allJobs;
      const job = resolveJobFromCommand(lowerCommand, candidateJobs, extractJobTitle);
      if (job) {
        openApplicationForm(job);
      } else {
        const templates = {
          'en-US': 'I could not tell which job you mean. Try "search for jobs" first, then say "apply to job 1".',
          'hi-IN': 'मुझे समझ नहीं आया कौन सी नौकरी। पहले "search jobs" कहें, फिर "apply to job 1" कहें।',
          'mr-IN': 'मला कोणती नोकरी हे कळले नाही. आधी "search jobs" म्हणा, मग "apply to job 1" म्हणा.',
        };
        const msg = templates[currentLanguage] || templates['en-US'];
        addMessage('assistant', msg, currentLanguage);
        speakResponse(msg, currentLanguage);
      }
    }

    // RATE A JOB - e.g. "rate job 1 five stars", "rate the second job 4 stars"
    else if (lowerCommand.includes('rate ') || lowerCommand.includes('रेटिंग') || lowerCommand.includes('स्टार')) {
      const candidateJobs = searchResults.length > 0 ? searchResults : allJobs;
      const job = resolveJobFromCommand(lowerCommand, candidateJobs, extractJobTitle);
      const rating = extractStarRating(lowerCommand);

      if (!job) {
        const templates = {
          'en-US': 'I could not tell which job you mean. Try "search for jobs" first, then say "rate job 1 five stars".',
          'hi-IN': 'मुझे समझ नहीं आया कौन सी नौकरी। पहले नौकरियां खोजें, फिर "rate job 1 five stars" कहें।',
          'mr-IN': 'मला कोणती नोकरी हे कळले नाही. आधी नोकऱ्या शोधा, मग "rate job 1 five stars" म्हणा.',
        };
        const msg = templates[currentLanguage] || templates['en-US'];
        addMessage('assistant', msg, currentLanguage);
        speakResponse(msg, currentLanguage);
      } else if (!rating) {
        const templates = {
          'en-US': `How many stars out of 5 for "${job.title}"? Say something like "5 stars".`,
          'hi-IN': `"${job.title}" के लिए 5 में से कितने स्टार? "5 स्टार" जैसा कुछ कहें।`,
          'mr-IN': `"${job.title}" साठी 5 पैकी किती स्टार? "5 स्टार" असे काहीतरी म्हणा.`,
        };
        const msg = templates[currentLanguage] || templates['en-US'];
        addMessage('assistant', msg, currentLanguage);
        speakResponse(msg, currentLanguage);
      } else {
        rateJobByVoice(job, rating);
      }
    }

    // APPROVE A PENDING JOB (admin only) - e.g. "approve job 1", "approve the backend engineer job"
    else if (isAdmin && (lowerCommand.includes('approve') || lowerCommand.includes('स्वीकृत') || lowerCommand.includes('मंजूर'))) {
      const job = resolveJobFromCommand(lowerCommand, pendingJobs, extractJobTitle);
      if (job) {
        approveJobByVoice(job);
      } else {
        const msg = getResponse('no_pending_job_found', currentLanguage);
        addMessage('assistant', msg, currentLanguage);
        speakResponse(msg, currentLanguage);
      }
    }

    // REJECT A PENDING JOB (admin only) - e.g. "reject job 1 because it's spam"
    else if (isAdmin && (lowerCommand.includes('reject') || lowerCommand.includes('अस्वीकृत') || lowerCommand.includes('नकार'))) {
      const job = resolveJobFromCommand(lowerCommand, pendingJobs, extractJobTitle);
      if (job) {
        const reason = extractRejectReason(lowerCommand) || 'Rejected via voice assistant.';
        rejectJobByVoice(job, reason);
      } else {
        const msg = getResponse('no_pending_job_found', currentLanguage);
        addMessage('assistant', msg, currentLanguage);
        speakResponse(msg, currentLanguage);
      }
    }

    // SEARCH JOBS BY TITLE - This handles specific job title searches
    else if (
      // Check for search patterns
      lowerCommand.includes('find ') ||
      lowerCommand.includes('search for ') ||
      lowerCommand.includes('looking for ') ||
      lowerCommand.includes('खोजें ') ||
      lowerCommand.includes('शोधा ') ||
      // Check for job title keywords
      lowerCommand.includes('developer') ||
      lowerCommand.includes('engineer') ||
      lowerCommand.includes('scientist') ||
      lowerCommand.includes('analyst') ||
      lowerCommand.includes('manager') ||
      lowerCommand.includes('डेवलपर') ||
      lowerCommand.includes('इंजीनियर') ||
      lowerCommand.includes('साइंटिस्ट')
    ) {
      const jobTitle = extractJobTitle(command);
      if (jobTitle) {
        searchJobsByTitle(jobTitle);
      } else {
        // If no specific title found but it's a search command, go to jobs page
        navigate('/jobs');
        addMessage('assistant', 'Opening jobs page.', currentLanguage);
        speakResponse('Opening jobs page.', currentLanguage);
      }
    }
    
    // Applications command - what this means depends on the caller's role (see goToApplications)
    else if (detectedCommand === 'my applications') {
      goToApplications();
    }

    // Navigation commands - also role-aware (see goToDashboard)
    else if (detectedCommand === 'go to dashboard') {
      goToDashboard();
    }
    else if (detectedCommand === 'my profile') {
      navigate('/profile');
      addMessage('assistant', getResponse('opening_profile', currentLanguage), currentLanguage);
      speakResponse(getResponse('opening_profile', currentLanguage), currentLanguage);
    }
    else if (detectedCommand === 'logout') {
      logOutByVoice();
    }
    else if (detectedCommand === 'post job') {
      navigate('/recruiter/dashboard', { state: { initialTab: 'jobs' } });
      addMessage('assistant', getResponse('opening_post_job', currentLanguage), currentLanguage);
      speakResponse(getResponse('opening_post_job', currentLanguage), currentLanguage);
    }
    else if (detectedCommand === 'discover candidates') {
      navigate('/recruiter/dashboard', { state: { initialTab: 'discover' } });
      addMessage('assistant', getResponse('opening_discover', currentLanguage), currentLanguage);
      speakResponse(getResponse('opening_discover', currentLanguage), currentLanguage);
    }
    else if (detectedCommand === 'view analytics') {
      goToAnalytics();
    }
    else if (detectedCommand === 'pending moderation') {
      navigate('/admin/dashboard', { state: { initialTab: 'pending-jobs' } });
      fetchPendingJobs(); // refresh in case the queue changed since mount
      addMessage('assistant', getResponse('opening_pending_moderation', currentLanguage), currentLanguage);
      speakResponse(getResponse('opening_pending_moderation', currentLanguage), currentLanguage);
    }
    else if (detectedCommand === 'upload resume') {
      navigate('/upload');
      addMessage('assistant', getResponse('opening_upload', currentLanguage), currentLanguage);
      speakResponse(getResponse('opening_upload', currentLanguage), currentLanguage);
    }
    else if (detectedCommand === 'go to home') {
      navigate('/');
      addMessage('assistant', getResponse('opening_home', currentLanguage), currentLanguage);
      speakResponse(getResponse('opening_home', currentLanguage), currentLanguage);
    }
    
    // Greeting
    else if (detectedCommand === 'hello') {
      const greetingTemplates = {
        'en-US': currentUser ? `Hello ${currentUser.fullName}! How can I help?` : "Hello! Please log in to use all features.",
        'hi-IN': currentUser ? `नमस्ते ${currentUser.fullName}! मैं आपकी कैसे मदद कर सकता हूँ?` : "नमस्ते! सभी सुविधाओं का उपयोग करने के लिए कृपया लॉग इन करें।",
        'mr-IN': currentUser ? `नमस्कार ${currentUser.fullName}! मी तुम्हाला कशी मदत करू शकतो?` : "नमस्कार! सर्व सुविधा वापरण्यासाठी कृपया लॉग इन करा."
      };
      addMessage('assistant', greetingTemplates[currentLanguage] || greetingTemplates['en-US'], currentLanguage);
      speakResponse(greetingTemplates[currentLanguage] || greetingTemplates['en-US'], currentLanguage);
    }

    // Stop speaking mid-sentence - deliberately doesn't speak the confirmation out loud, since
    // that would immediately restart the exact thing the user just asked to stop.
    else if (detectedCommand === 'stop') {
      window.speechSynthesis.cancel();
      setIsSpeaking(false);
      addMessage('assistant', getResponse('stopped_speaking', currentLanguage), currentLanguage);
    }

    // Repeat the last thing the assistant said, instead of making the user ask again.
    else if (detectedCommand === 'repeat') {
      const lastAssistantMessage = [...messages].reverse().find(m => m.type === 'assistant');
      if (lastAssistantMessage) {
        speakResponse(lastAssistantMessage.text, currentLanguage);
      } else {
        const msg = getResponse('nothing_to_repeat', currentLanguage);
        addMessage('assistant', msg, currentLanguage);
        speakResponse(msg, currentLanguage);
      }
    }

    // Unknown command
    else {
      addMessage('assistant', getResponse('unknown_command', currentLanguage), currentLanguage);
      speakResponse(getResponse('unknown_command', currentLanguage), currentLanguage);
    }
  };

  // A typed alternative to speaking - same command pipeline as a finished speech-recognition
  // result, so every command above works here too. Useful on browsers without Web Speech API
  // support (Firefox has none), in a noisy or shared space, or just when typing is faster.
  const handleTextSubmit = (e) => {
    e.preventDefault();
    const value = textInput.trim();
    if (!value) return;
    setTextInput('');
    handleVoiceCommand(value, currentLanguage);
  };

  const toggleListening = () => {
    if (!recognitionRef.current) {
      // Previously this added a message to a panel the user never saw opening - the only visible
      // effect of clicking the mic on an unsupported browser (e.g. Firefox) was nothing at all.
      // Open the panel so they actually see the message and can fall back to the text input below.
      setIsOpen(true);
      addMessage('assistant', getResponse('not_supported', currentLanguage), currentLanguage);
      return;
    }

    if (isListening) {
      setPlayStopSound(true); // Play stop sound
      stopAllAudio();
    } else {
      // Cancel any ongoing speech before starting to listen
      if (synthesisRef.current) {
        window.speechSynthesis.cancel();
        setIsSpeaking(false);
      }
      navigator.mediaDevices.getUserMedia({ audio: true })
        .then((stream) => {
          mediaStreamRef.current = stream;
          recognitionRef.current.start();
          if (!isOpen) setIsOpen(true);
        })
        .catch((err) => {
          console.error('Microphone error:', err);
          addMessage('assistant', getResponse('microphone_required', currentLanguage), currentLanguage);
        });
    }
  };

  const toggleSpeech = () => {
    setSpeechEnabled(!speechEnabled);
    if (!speechEnabled) {
      window.speechSynthesis.cancel();
      setIsSpeaking(false);
    }
  };

  const closeAssistant = () => {
    stopAllAudio();
    setIsOpen(false);
    setShowHelpPopup(false);
    setShowSearchResults(false);
  };

  const changeLanguage = (langCode) => {
    setCurrentLanguage(langCode);
    if (recognitionRef.current) {
      recognitionRef.current.lang = langCode;
    }
    
    const langMessages = {
      'en-US': 'Language changed to English. Say "help" to see commands.',
      'hi-IN': 'भाषा हिंदी में बदल दी गई है। कमांड देखने के लिए "मदद" कहें।',
      'mr-IN': 'भाषा मराठीमध्ये बदलली गेली आहे. कमांड पाहण्यासाठी "मदत" म्हणा.'
    };
    
    addMessage('assistant', langMessages[langCode], langCode);
    speakResponse(langMessages[langCode], langCode);
  };

  // Format date
  const formatDate = (dateString) => {
    if (!dateString) return 'Recently';
    const date = new Date(dateString);
    const now = new Date();
    const diffTime = Math.abs(now - date);
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
    return date.toLocaleDateString();
  };

  // Parse skills
  const parseSkills = (skillsJson) => {
    try {
      if (!skillsJson) return [];
      if (typeof skillsJson === 'string') {
        return JSON.parse(skillsJson);
      }
      return skillsJson;
    } catch {
      return [];
    }
  };

  // Job Card Component
  const JobCard = ({ job, onApply }) => {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-3 hover:shadow-md transition-shadow mb-2">
        <div className="flex justify-between items-start mb-2">
          <div>
            <h4 className="font-semibold text-gray-800">{job.title}</h4>
            <p className="text-sm text-gray-600">{job.company}</p>
          </div>
          {job.matchScore && (
            <span className="px-2 py-1 rounded-full text-xs font-semibold bg-green-100 text-green-800">
              {job.matchScore}% Match
            </span>
          )}
        </div>
        
        <div className="flex flex-wrap gap-2 text-xs text-gray-500 mb-2">
          {job.location && (
            <span className="flex items-center">
              <MapPin className="h-3 w-3 mr-1" />
              {job.location}
            </span>
          )}
          {job.salaryRange && (
            <span className="flex items-center">
              <DollarSign className="h-3 w-3 mr-1" />
              {job.salaryRange}
            </span>
          )}
          {job.jobType && (
            <span className="flex items-center">
              <Briefcase className="h-3 w-3 mr-1" />
              {job.jobType}
            </span>
          )}
          <span className="flex items-center">
            <Clock className="h-3 w-3 mr-1" />
            {formatDate(job.postedDate)}
          </span>
        </div>

        {/* Skills preview */}
        {job.requiredSkills && (
          <div className="flex flex-wrap gap-1 mb-2">
            {parseSkills(job.requiredSkills).slice(0, 3).map((skill, i) => (
              <span key={i} className="bg-blue-100 text-blue-800 px-2 py-0.5 rounded text-xs">
                {skill}
              </span>
            ))}
            {parseSkills(job.requiredSkills).length > 3 && (
              <span className="text-gray-500 text-xs">
                +{parseSkills(job.requiredSkills).length - 3} more
              </span>
            )}
          </div>
        )}

        <button
          onClick={() => onApply(job)}
          className="w-full mt-2 bg-green-600 text-white px-3 py-2 rounded-lg text-sm font-medium hover:bg-green-700 transition-colors flex items-center justify-center"
        >
          <Briefcase className="h-4 w-4 mr-2" />
          Apply Now
        </button>
      </div>
    );
  };

  // Application Card Component
  const ApplicationCard = ({ application }) => {
    const getStatusColor = (status) => {
      switch (status) {
        case 'APPLICATION_SUBMITTED': return 'bg-blue-100 text-blue-800';
        case 'VIEWED_BY_RECRUITER': return 'bg-purple-100 text-purple-800';
        case 'SHORTLISTED': return 'bg-green-100 text-green-800';
        case 'INTERVIEW_SCHEDULED': return 'bg-yellow-100 text-yellow-800';
        case 'OFFER_SENT': return 'bg-emerald-100 text-emerald-800';
        case 'REJECTED': return 'bg-red-100 text-red-800';
        default: return 'bg-gray-100 text-gray-800';
      }
    };

    return (
      <div className="bg-white border border-gray-200 rounded-lg p-3 mb-2">
        <div className="flex justify-between items-start">
          <div>
            <h4 className="font-semibold text-gray-800">{application.job?.title}</h4>
            <p className="text-sm text-gray-600">{application.job?.company}</p>
          </div>
          <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(application.status)}`}>
            {application.status.replace(/_/g, ' ').toLowerCase()}
          </span>
        </div>
        <div className="mt-2 flex justify-between items-center text-xs text-gray-500">
          <span>Applied: {formatDate(application.applicationDate)}</span>
        </div>
      </div>
    );
  };

  return (
    <div className="fixed bottom-6 right-6 z-50">
      {/* Help Popup - UPDATED with all commands */}
      {showHelpPopup && (
        <div className="absolute bottom-20 right-0 mb-4 w-96 bg-white rounded-lg shadow-2xl border-2 border-blue-500 z-50">
          <div className="bg-gradient-to-r from-blue-600 to-purple-600 text-white p-3 rounded-t-lg flex justify-between items-center">
            <span className="font-bold flex items-center">
              <HelpCircle className="h-5 w-5 mr-2" />
              {currentLanguage === 'en-US' && 'All Voice Commands'}
              {currentLanguage === 'hi-IN' && 'सभी वॉयस कमांड'}
              {currentLanguage === 'mr-IN' && 'सर्व व्हॉइस कमांड'}
            </span>
            <button
              onClick={() => setShowHelpPopup(false)}
              aria-label="Close help"
              className="hover:bg-white hover:bg-opacity-20 rounded p-1"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          
          <div className="p-4 max-h-96 overflow-y-auto">
            {getCommandSections(currentLanguage, { isRecruiter, isAdmin }).map((section, idx) => (
              <div key={idx} className="mb-4">
                <h3 className="font-bold text-gray-700 mb-2">{section.category}</h3>
                <div className="space-y-1">
                  {section.commands.map((cmd, cmdIdx) => (
                    <button
                      key={cmdIdx}
                      onClick={() => executeCommand(cmd.action)}
                      className="w-full text-left text-sm text-gray-600 hover:bg-blue-50 p-2 rounded transition-colors cursor-pointer border border-transparent hover:border-blue-200"
                    >
                      {cmd.text}
                    </button>
                  ))}
                </div>
              </div>
            ))}
            
            <div className="mt-4 p-2 bg-yellow-50 border border-yellow-200 rounded-lg">
              <p className="text-xs text-yellow-800">
                {currentLanguage === 'en-US' && '💡 Try: "recommend jobs", "browse jobs", or search "find data scientist jobs". Say "stop" to interrupt me, or "repeat that" to hear it again.'}
                {currentLanguage === 'hi-IN' && '💡 उदाहरण: "recommend jobs", "browse jobs", या "डाटा साइंटिस्ट नौकरी" खोजें। रोकने के लिए "stop" कहें, दोहराने के लिए "repeat that" कहें।'}
                {currentLanguage === 'mr-IN' && '💡 उदाहरण: "recommend jobs", "browse jobs", किंवा "डेटा सायंटिस्ट नोकरी" शोधा. थांबवण्यासाठी "stop" म्हणा, पुन्हा ऐकण्यासाठी "repeat that" म्हणा.'}
              </p>
              <p className="text-xs text-yellow-700 mt-1">
                {currentLanguage === 'en-US' && '⌨️ Press Alt+A anywhere on the page to open the assistant.'}
                {currentLanguage === 'hi-IN' && '⌨️ पेज पर कहीं भी Alt+A दबाकर असिस्टेंट खोलें।'}
                {currentLanguage === 'mr-IN' && '⌨️ पेजवर कुठेही Alt+A दाबून सहाय्यक उघडा.'}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Main Assistant Window */}
      {isOpen && (
        <div className="mb-4 w-80 bg-white rounded-lg shadow-xl border border-gray-200">
          {/* Header */}
          <div className="p-4 bg-blue-600 text-white rounded-t-lg">
            <div className="flex justify-between items-center mb-2">
              <span className="font-semibold">DreamRole Assistant</span>
              <div className="flex items-center space-x-2">
                <button 
                  onClick={showHelpCommands} 
                  className="p-1 hover:bg-blue-700 rounded"
                  title="Show all commands"
                >
                  <HelpCircle className="h-4 w-4" />
                </button>
                <button onClick={toggleSpeech} className="p-1 hover:bg-blue-700 rounded">
                  {speechEnabled ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
                </button>
                <button 
                  onClick={closeAssistant} 
                  className="p-1 hover:bg-blue-700 rounded"
                  title="Close assistant"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
            </div>
            
            {/* Language selector */}
            <div className="flex space-x-2 mt-2">
              <button
                onClick={() => changeLanguage('en-US')}
                className={`flex-1 text-xs py-1 px-2 rounded ${
                  currentLanguage === 'en-US' 
                    ? 'bg-white text-blue-600 font-semibold' 
                    : 'bg-blue-500 text-white hover:bg-blue-400'
                }`}
              >
                English
              </button>
              <button
                onClick={() => changeLanguage('hi-IN')}
                className={`flex-1 text-xs py-1 px-2 rounded ${
                  currentLanguage === 'hi-IN' 
                    ? 'bg-white text-blue-600 font-semibold' 
                    : 'bg-blue-500 text-white hover:bg-blue-400'
                }`}
              >
                हिन्दी
              </button>
              <button
                onClick={() => changeLanguage('mr-IN')}
                className={`flex-1 text-xs py-1 px-2 rounded ${
                  currentLanguage === 'mr-IN' 
                    ? 'bg-white text-blue-600 font-semibold' 
                    : 'bg-blue-500 text-white hover:bg-blue-400'
                }`}
              >
                मराठी
              </button>
            </div>
          </div>

          {/* Messages */}
          <div className="h-64 overflow-y-auto p-3 space-y-2">
            {messages.map((msg, i) => (
              <div key={i}>
                <div className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div className={`max-w-xs px-3 py-2 rounded-lg text-sm ${
                    msg.type === 'user' 
                      ? 'bg-blue-600 text-white' 
                      : 'bg-gray-100 text-gray-800'
                  }`}>
                    {msg.text}
                  </div>
                </div>
                
                {/* Show job results */}
                {msg.data?.jobs && (
                  <div className="mt-2 space-y-2">
                    {msg.data.jobs.map((job, idx) => (
                      <JobCard 
                        key={idx} 
                        job={job} 
                        onApply={() => openApplicationForm(job)}
                      />
                    ))}
                    {searchResults.length > 5 && (
                      <button
                        onClick={() => navigate('/jobs')}
                        className="w-full text-center text-sm text-blue-600 hover:text-blue-800 font-medium"
                      >
                        See all {searchResults.length} results →
                      </button>
                    )}
                  </div>
                )}
                
                {/* Show application results */}
                {msg.data?.applications && (
                  <div className="mt-2 space-y-2">
                    {msg.data.applications.map((app, idx) => (
                      <ApplicationCard key={idx} application={app} />
                    ))}
                  </div>
                )}
              </div>
            ))}
            
            {isProcessing && (
              <div className="flex justify-start">
                <div className="bg-gray-100 px-3 py-2 rounded-lg text-sm">
                  <div className="flex items-center space-x-2">
                    <div className="animate-spin rounded-full h-3 w-3 border-b-2 border-blue-600"></div>
                    <span>Searching...</span>
                  </div>
                </div>
              </div>
            )}
            
            {transcript && isListening && (
              <div className="flex justify-end">
                <div className="bg-blue-400 text-white px-3 py-2 rounded-lg text-sm">
                  {transcript}...
                </div>
              </div>
            )}
            
            {isSpeaking && (
              <div className="flex justify-start">
                <div className="bg-green-100 text-green-800 px-3 py-2 rounded-lg text-sm flex items-center">
                  <Volume2 className="h-3 w-3 mr-1 animate-pulse" />
                  {currentLanguage === 'en-US' && 'Speaking...'}
                  {currentLanguage === 'hi-IN' && 'बोल रहा हूँ...'}
                  {currentLanguage === 'mr-IN' && 'बोलत आहे...'}
                </div>
              </div>
            )}
            
            <div ref={messagesEndRef} />
          </div>

          {/* Type a command - works identically to speaking one, and is the only option on
              browsers (e.g. Firefox) with no Web Speech API support. */}
          <form onSubmit={handleTextSubmit} className="flex items-center gap-2 p-2 border-t">
            <input
              ref={textInputRef}
              type="text"
              value={textInput}
              onChange={(e) => setTextInput(e.target.value)}
              placeholder={
                currentLanguage === 'hi-IN' ? 'कमांड टाइप करें...'
                  : currentLanguage === 'mr-IN' ? 'कमांड टाइप करा...'
                  : 'Or type a command...'
              }
              aria-label="Type a voice assistant command"
              className="flex-1 text-sm border border-gray-300 rounded-full px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-400"
            />
            <button
              type="submit"
              disabled={!textInput.trim()}
              className="text-sm font-medium text-blue-600 hover:text-blue-800 disabled:opacity-40 disabled:cursor-not-allowed px-2"
            >
              {currentLanguage === 'hi-IN' ? 'भेजें' : currentLanguage === 'mr-IN' ? 'पाठवा' : 'Send'}
            </button>
          </form>

          {/* Footer */}
          <div className="p-2 border-t text-center text-xs text-gray-500">
            <button 
              onClick={showHelpCommands}
              className="text-blue-600 hover:text-blue-800 font-medium"
            >
              {currentLanguage === 'en-US' && '🔍 Click for all voice commands'}
              {currentLanguage === 'hi-IN' && '🔍 सभी वॉयस कमांड के लिए क्लिक करें'}
              {currentLanguage === 'mr-IN' && '🔍 सर्व व्हॉइस कमांडसाठी क्लिक करा'}
            </button>
          </div>
        </div>
      )}

      {/* Microphone button */}
      <button
        onClick={toggleListening}
        className={`p-4 rounded-full shadow-lg transition-all ${
          isListening 
            ? 'bg-red-500 hover:bg-red-600 animate-pulse' 
            : 'bg-blue-600 hover:bg-blue-700'
        } text-white`}
        title={isListening ? 'Stop listening' : 'Start voice assistant'}
      >
        {isListening ? <MicOff className="h-6 w-6" /> : <Mic className="h-6 w-6" />}
      </button>

      {/* Application Modal */}
      {showApplicationModal && selectedJobForApplication && (
        <JobApplicationModal
          job={selectedJobForApplication}
          onClose={handleApplicationClose}
          onSuccess={handleApplicationSuccess}
        />
      )}
    </div>
  );
};

export default VoiceAssistant;