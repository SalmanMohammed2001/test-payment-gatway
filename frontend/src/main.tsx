import React from 'react';
import ReactDOM from 'react-dom/client';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';
import CheckoutPage from './pages/CheckoutPage';
import ResultPage from './pages/ResultPage';
import './styles.css';

const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/checkout" replace /> },
  { path: '/checkout', element: <CheckoutPage /> },
  { path: '/checkout/result', element: <ResultPage /> },
]);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>,
);
