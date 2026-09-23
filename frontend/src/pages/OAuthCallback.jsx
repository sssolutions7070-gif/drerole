import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import authService from '../services/auth';

// Landing point for the "Sign in with Google/LinkedIn" redirect flow: the backend already
// exchanged the code and sends the browser here with just a token on the URL.
const OAuthCallback = ({ onAuthChange }) => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    const token = searchParams.get('token');
    if (!token) {
      navigate('/login', { replace: true });
      return;
    }

    authService.completeOAuthLogin(token)
      .then((user) => {
        onAuthChange(user);
        navigate(user.role === 'ROLE_RECRUITER' ? '/recruiter/dashboard' : '/dashboard', { replace: true });
      })
      .catch((err) => {
        setError(err.error || 'Sign-in failed. Please try again.');
      });
  }, [searchParams, navigate, onAuthChange]);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center">
        {error ? (
          <>
            <p className="text-red-600 mb-4">{error}</p>
            <a href="/login" className="text-blue-600 hover:text-blue-500 font-medium">Back to sign in</a>
          </>
        ) : (
          <>
            <div className="w-12 h-12 border-4 border-blue-600 border-t-transparent rounded-full animate-spin mx-auto"></div>
            <p className="mt-4 text-gray-600">Signing you in...</p>
          </>
        )}
      </div>
    </div>
  );
};

export default OAuthCallback;
