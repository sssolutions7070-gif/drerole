import api from './api';

class AuthService {
  async register(userData) {
    try {
      const response = await api.post('/auth/register', userData);
      if (response.data.token) {
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(response.data.user));
      }
      return response.data;
    } catch (error) {
      throw error.response?.data || { error: 'Registration failed' };
    }
  }

  async login(credentials) {
    try {
      const response = await api.post('/auth/login', credentials);
      // When the account has 2FA enabled, the response carries requiresTwoFactor + userId and no
      // token yet - nothing to store until loginWithTwoFactor() completes the second step.
      if (response.data.token) {
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(response.data.user));
      }
      return response.data;
    } catch (error) {
      throw error.response?.data || { error: 'Login failed' };
    }
  }

  async loginWithTwoFactor(userId, code) {
    try {
      const response = await api.post('/auth/login/2fa', { userId, code });
      if (response.data.token) {
        localStorage.setItem('token', response.data.token);
        localStorage.setItem('user', JSON.stringify(response.data.user));
      }
      return response.data;
    } catch (error) {
      throw error.response?.data || { error: 'Verification failed' };
    }
  }

  async forgotPassword(email) {
    try {
      const response = await api.post('/auth/forgot-password', { email });
      return response.data;
    } catch (error) {
      throw error.response?.data || { error: 'Request failed' };
    }
  }

  async resetPassword(token, password) {
    try {
      const response = await api.post('/auth/reset-password', { token, password });
      return response.data;
    } catch (error) {
      throw error.response?.data || { error: 'Reset failed' };
    }
  }

  logout() {
    const token = this.getToken();
    if (token) {
      // Call logout endpoint
      api.post('/auth/logout', {}, {
        headers: { Authorization: `Bearer ${token}` }
      }).catch(() => {}); // Ignore errors during logout
    }
    
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    window.location.href = '/';
  }

  getToken() {
    return localStorage.getItem('token');
  }

  getCurrentUser() {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
  }

  isAuthenticated() {
    return !!this.getToken();
  }

  async checkAuth() {
    const token = this.getToken();
    if (!token) return null;

    try {
      const response = await api.get('/auth/me', {
        headers: { Authorization: `Bearer ${token}` }
      });
      return response.data.user;
    } catch (error) {
      this.logout();
      return null;
    }
  }
}

export default new AuthService();