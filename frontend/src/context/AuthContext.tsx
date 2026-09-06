import React, { createContext, useContext, useState, useEffect } from 'react';
import authService from '../services/authService';
import type { User, LoginRequest, Role } from '../types';

interface AuthContextType {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isAdmin: boolean;
  isDeveloper: boolean;
  isTester: boolean;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const savedToken = authService.getToken();
    if (savedToken) {
      if (authService.isTokenExpired(savedToken)) {
        authService.logout();
        setUser(null);
        setToken(null);
      } else {
        const storedUser = authService.getStoredUser();
        if (storedUser) {
          setUser(storedUser);
          setToken(savedToken);
        } else {
          const payload = authService.decodeToken(savedToken);
          if (payload) {
            const reconstructedUser: User = {
              email: payload.sub,
              role: (payload.role as Role) || 'DEVELOPER',
              name: payload.sub.split('@')[0],
            };
            setUser(reconstructedUser);
            setToken(savedToken);
            localStorage.setItem('bugpilot_user', JSON.stringify(reconstructedUser));
          }
        }
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (credentials: LoginRequest): Promise<void> => {
    setIsLoading(true);
    try {
      const { token: receivedToken, user: loggedUser } = await authService.login(credentials);
      setToken(receivedToken);
      setUser(loggedUser);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    authService.logout();
    setUser(null);
    setToken(null);
  };

  const role = user?.role;
  const isAdmin = role === 'ADMIN';
  const isDeveloper = role === 'DEVELOPER' || role === 'ADMIN';
  const isTester = role === 'TESTER';

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token,
        isLoading,
        isAdmin,
        isDeveloper,
        isTester,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export default AuthContext;
