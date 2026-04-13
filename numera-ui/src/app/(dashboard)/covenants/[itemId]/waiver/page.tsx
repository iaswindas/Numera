'use client'

import { useMemo, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import LetterPreview from '@/components/covenant/LetterPreview'
import WaiverForm, { type WaiverFormValues } from '@/components/covenant/WaiverForm'
import {
  useDownloadWaiverLetter,
  useGenerateWaiverLetter,
  useSendWaiverLetter,
  useSignatures,
  useEmailTemplates,
  useMonitoringItemContacts,
} from '@/services/covenantApi'
import { useToast } from '@/components/ui/Toast'

type DecisionState = {
  waiverType: 'WAIVE' | 'NOT_WAIVE'
  durationType: 'INSTANCE' | 'PERMANENT'
  comments: string
}

export default function WaiverLetterPage() {
  const params = useParams<{ itemId: string }>()
  const router = useRouter()
  const { showToast } = useToast()

  const [step, setStep] = useState(1)
  const [decision, setDecision] = useState<DecisionState>({
    waiverType: 'WAIVE',
    durationType: 'INSTANCE',
    comments: '',
  })
  const [waiverForm, setWaiverForm] = useState<WaiverFormValues | null>(null)
  const [letterId, setLetterId] = useState<string>('')
  const [letterSubject, setLetterSubject] = useState('')
  const [letterContent, setLetterContent] = useState('')

  const signaturesQuery = useSignatures()
  const templatesQuery = useEmailTemplates()
  const contactsQuery = useMonitoringItemContacts(itemId)

  const generateMutation = useGenerateWaiverLetter()
  const sendMutation = useSendWaiverLetter()
  const downloadMutation = useDownloadWaiverLetter(letterId)

  const itemId = String(params.itemId ?? '')

  const templates = useMemo(
    () => ((templatesQuery.data as Array<{ id: string; name: string }> | undefined) ?? []),
    [templatesQuery.data]
  )

  const contacts = useMemo(
    () => {
      const apiContacts = contactsQuery.data as Array<{ id: string; name: string; email: string }> | undefined
      if (apiContacts && apiContacts.length > 0) return apiContacts
      // Fallback when API returns no contacts
      return [
        { id: `${itemId}-1`, name: 'Primary Contact', email: 'primary.contact@customer.com' },
        { id: `${itemId}-2`, name: 'Finance Controller', email: 'finance.controller@customer.com' },
      ]
    },
    [contactsQuery.data, itemId]
  )

  const onGeneratePreview = async (values: WaiverFormValues) => {
    setWaiverForm(values)
    try {
      const generated = await generateMutation.mutateAsync({
        itemId,
        payload: {
          waiverType: decision.waiverType,
          durationType: decision.durationType,
          comments: decision.comments,
          templateId: values.templateId,
          signatureId: values.signatureId,
          recipientEmails: values.recipients,
        },
      })
      setLetterId(generated.id)
      setLetterSubject(generated.subject)
      setLetterContent(generated.bodyHtml)
      setStep(3)
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to generate waiver letter'
      showToast(message, 'error')
    }
  }

  const onSend = async () => {
    if (!waiverForm || !letterId) {
      showToast('Generate a waiver letter before sending', 'info')
      return
    }

    try {
      await sendMutation.mutateAsync({
        id: letterId,
        payload: {
          recipientEmails: waiverForm.recipients,
          message: letterContent,
        },
      })
      showToast('Waiver letter sent successfully', 'success')
      router.push('/covenants')
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to send waiver letter'
      showToast(message, 'error')
    }
  }

  const onDownload = async () => {
    if (!letterId) {
      showToast('Generate a waiver letter before download', 'info')
      return
    }

    try {
      const blob = await downloadMutation.mutateAsync()
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `waiver-letter-${letterId}.pdf`
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      const message =
        typeof error === 'object' && error && 'message' in error
          ? String((error as { message: string }).message)
          : 'Failed to download waiver letter'
      showToast(message, 'error')
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Waiver Letter</h1>
        <p>Monitoring Item: {itemId}</p>
      </div>

      <div className="tabs" style={{ marginBottom: 20 }}>
        <button className={`tab ${step === 1 ? 'active' : ''}`} onClick={() => setStep(1)}>1. Waiver Type</button>
        <button className={`tab ${step === 2 ? 'active' : ''}`} onClick={() => setStep(2)}>2. Template & Contacts</button>
        <button className={`tab ${step === 3 ? 'active' : ''}`} onClick={() => setStep(3)} disabled={!letterContent}>3. Preview</button>
      </div>

      {step === 1 ? (
        <div className="card" style={{ maxWidth: 860 }}>
          <div className="card-header">
            <div>
              <div className="card-title">Step 1: Waiver Type</div>
              <div className="card-subtitle">Choose waiver decision and rationale</div>
            </div>
          </div>

          <div className="grid-2" style={{ marginBottom: 16 }}>
            <div className="input-group">
              <label>Waiver Decision</label>
              <div style={{ display: 'flex', gap: 14 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="radio"
                    checked={decision.waiverType === 'WAIVE'}
                    onChange={() => setDecision((prev) => ({ ...prev, waiverType: 'WAIVE' }))}
                  />
                  Waive
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="radio"
                    checked={decision.waiverType === 'NOT_WAIVE'}
                    onChange={() => setDecision((prev) => ({ ...prev, waiverType: 'NOT_WAIVE' }))}
                  />
                  Not Waive
                </label>
              </div>
            </div>

            <div className="input-group">
              <label>Duration</label>
              <div style={{ display: 'flex', gap: 14 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="radio"
                    checked={decision.durationType === 'INSTANCE'}
                    onChange={() => setDecision((prev) => ({ ...prev, durationType: 'INSTANCE' }))}
                  />
                  Instance
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input
                    type="radio"
                    checked={decision.durationType === 'PERMANENT'}
                    onChange={() => setDecision((prev) => ({ ...prev, durationType: 'PERMANENT' }))}
                  />
                  Permanent
                </label>
              </div>
            </div>
          </div>

          <div className="input-group">
            <label>Comments</label>
            <textarea
              className="input"
              rows={6}
              value={decision.comments}
              onChange={(event) => setDecision((prev) => ({ ...prev, comments: event.target.value }))}
              placeholder="Provide rationale and any legal or relationship context"
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
            <button className="btn btn-primary" onClick={() => setStep(2)}>Continue</button>
          </div>
        </div>
      ) : null}

      {step === 2 ? (
        <div className="card" style={{ maxWidth: 960 }}>
          <div className="card-header">
            <div>
              <div className="card-title">Step 2: Template & Contacts</div>
              <div className="card-subtitle">Select template, signature, and recipients</div>
            </div>
          </div>

          {templatesQuery.isLoading || signaturesQuery.isLoading ? (
            <div style={{ color: 'var(--text-muted)' }}>Loading templates and signatures...</div>
          ) : null}

          {templatesQuery.isError || signaturesQuery.isError ? (
            <div style={{ color: 'var(--danger)' }}>Failed to load templates or signatures.</div>
          ) : null}

          {!templatesQuery.isLoading && !signaturesQuery.isLoading && !templatesQuery.isError && !signaturesQuery.isError ? (
            <WaiverForm
              defaultValues={{
                waiverType: decision.waiverType,
                durationType: decision.durationType,
                comments: decision.comments,
                templateId: waiverForm?.templateId,
                signatureId: waiverForm?.signatureId,
                recipients: waiverForm?.recipients,
              }}
              templates={templates}
              signatures={signaturesQuery.data ?? []}
              contacts={contacts}
              onSubmit={onGeneratePreview}
              showDecisionFields={false}
            />
          ) : null}
        </div>
      ) : null}

      {step === 3 ? (
        <>
          <LetterPreview
            subject={letterSubject}
            content={letterContent}
            onChange={setLetterContent}
            onPrint={onDownload}
            onSendEmail={onSend}
            onDownloadPdf={onDownload}
          />

          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
            <button className="btn btn-secondary" onClick={() => router.push('/covenants')}>Cancel</button>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn-secondary" onClick={onDownload} disabled={downloadMutation.isPending}>
                Download PDF
              </button>
              <button className="btn btn-primary" onClick={onSend} disabled={sendMutation.isPending || generateMutation.isPending}>
                Send via Email
              </button>
            </div>
          </div>
        </>
      ) : null}
    </>
  )
}
