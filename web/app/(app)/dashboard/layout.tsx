'use client'

import { Home, MessageSquareText, UserCircle, Users } from 'lucide-react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import AccountDeletionAlert from './(components)/account-deletion-alert'
import UpgradeToProAlert from './(components)/upgrade-to-pro-alert'
import UpdateAppModal from './(components)/update-app-modal'
import UpdateAppNotificationBar from './(components)/update-app-notification-bar'
import VerifyEmailAlert from './(components)/verify-email-alert'
import { SurveyModal } from '@/components/shared/survey-modal'

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const pathname = usePathname()

  return (
    <div className='flex min-h-screen flex-col md:flex-row'>
      {/* Sidebar for desktop */}
      <aside className='hidden md:flex flex-col fixed top-[20%] left-0 w-24 bg-white border-r border-purple-100 shadow-lg z-10 rounded-r-lg'>
        <nav className='flex flex-col justify-center items-center h-full py-3 space-y-4'>
          <NavItem
            href='/dashboard'
            icon={<Home className='h-6 w-6 stroke-[1.5]' />}
            label='Dashboard'
            isActive={pathname === '/dashboard'}
          />
          <NavItem
            href='/dashboard/messaging'
            icon={<MessageSquareText className='h-6 w-6 stroke-[1.5]' />}
            label='Messaging'
            isActive={pathname === '/dashboard/messaging'}
          />
          <NavItem
            href='/dashboard/community'
            icon={<Users className='h-6 w-6 stroke-[1.5]' />}
            label='Community'
            isActive={pathname === '/dashboard/community'}
          />
          <NavItem
            href='/dashboard/account'
            icon={<UserCircle className='h-6 w-6 stroke-[1.5]' />}
            label='Account'
            isActive={pathname === '/dashboard/account'}
          />
        </nav>
      </aside>

      {/* Main content with left padding to account for fixed sidebar */}
      <main className='flex-1 min-w-0 overflow-auto md:ml-24'>
        <div className='space-y-2 p-4'>
          <UpdateAppNotificationBar />
          <VerifyEmailAlert />
          <AccountDeletionAlert />
          <UpgradeToProAlert />
          {/* <BlackFridayModal /> */}
        </div>
        {children}
      </main>

      {/* Bottom navigation for mobile */}
      <nav className='md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-purple-100 shadow-lg z-10'>
        <div className='flex items-center justify-around h-16'>
          <MobileNavItem
            href='/dashboard'
            icon={<Home className='h-5 w-5 stroke-[1.5]' />}
            label='Dashboard'
            isActive={pathname === '/dashboard'}
          />
          <MobileNavItem
            href='/dashboard/messaging'
            icon={<MessageSquareText className='h-5 w-5 stroke-[1.5]' />}
            label='Messaging'
            isActive={pathname === '/dashboard/messaging'}
          />
          <MobileNavItem
            href='/dashboard/community'
            icon={<Users className='h-5 w-5 stroke-[1.5]' />}
            label='Community'
            isActive={pathname === '/dashboard/community'}
          />
          <MobileNavItem
            href='/dashboard/account'
            icon={<UserCircle className='h-5 w-5 stroke-[1.5]' />}
            label='Account'
            isActive={pathname === '/dashboard/account'}
          />
        </div>
      </nav>

      {/* Bottom padding for mobile to account for the fixed navigation */}
      <div className='h-16 md:hidden'></div>

      <SurveyModal />
      <UpdateAppModal />
    </div>
  )
}

// Desktop navigation item
function NavItem({
  href,
  icon,
  label,
  isActive,
}: {
  href: string
  icon: React.ReactNode
  label: string
  isActive: boolean
}) {
  return (
    <Link
      href={href}
      prefetch={true}
      className={`flex flex-col items-center p-2 rounded-md transition-colors w-20 ${isActive
        ? 'border border-brand-500 bg-brand-50 text-brand-700'
        : 'text-gray-700 hover:bg-brand-50 hover:text-brand-700'
        }`}
    >
      <span
        className={
          isActive
            ? 'text-brand-600 mb-1'
            : 'text-gray-600 mb-1'
        }
      >
        {icon}
      </span>
      <span className='font-medium text-xs'>{label}</span>
    </Link>
  )
}

// Mobile navigation item
function MobileNavItem({
  href,
  icon,
  label,
  isActive,
}: {
  href: string
  icon: React.ReactNode
  label: string
  isActive: boolean
}) {
  return (
    <Link
      href={href}
      prefetch={true}
      className={`flex flex-col items-center justify-center p-2 rounded-md w-[23%] ${isActive
        ? 'border border-brand-500 bg-brand-50 text-brand-700'
        : 'text-gray-700 hover:text-brand-600'
        }`}
    >
      <span
        className={
          isActive
            ? 'text-brand-600'
            : 'text-gray-600'
        }
      >
        {icon}
      </span>
      <span className='text-xs mt-1'>{label}</span>
    </Link>
  )
}
